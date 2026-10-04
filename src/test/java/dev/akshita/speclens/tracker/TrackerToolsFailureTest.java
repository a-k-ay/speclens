package dev.akshita.speclens.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import dev.akshita.speclens.tracker.ToolCallRecord.Outcome;
import org.junit.jupiter.api.Test;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.mock.env.MockEnvironment;

/** The tools' safety behaviour, with no tracker running at all. */
class TrackerToolsFailureTest {

	// Port 1 is never listening, so every call fails fast with "connection refused".
	private final TrackerClient unreachable = new TrackerClient(
			new TrackerProperties("http://127.0.0.1:1/rest/api/3", Duration.ofSeconds(1), Duration.ofSeconds(1)),
			new MockEnvironment());

	@Test
	void trackerDownBecomesAReadableErrorNotAnException() {
		TrackerTools tools = new TrackerTools(unreachable);

		TrackerTools.TicketsResult result = tools.getTicketsForRequirement("BR-8.1");

		assertThat(result.count()).isZero();
		assertThat(result.error()).startsWith("TRACKER_UNAVAILABLE");
		assertThat(tools.trackerWasUnavailable()).isTrue();
		assertThat(tools.calls()).singleElement().satisfies(c -> {
			assertThat(c.tool()).isEqualTo("getTicketsForRequirement");
			assertThat(c.outcome()).isEqualTo(Outcome.UNAVAILABLE);
		});
	}

	@Test
	void invalidArgumentsAreRejectedBeforeAnyHttpCall() {
		TrackerTools tools = new TrackerTools(unreachable);

		TrackerTools.TicketResult result = tools.getTicket("LOG-1; DROP TABLE tickets");

		// REJECTED, not UNAVAILABLE: validation stopped it before the (dead) tracker was called.
		assertThat(result.error()).startsWith("Invalid argument");
		assertThat(tools.calls()).singleElement().extracting(ToolCallRecord::outcome).isEqualTo(Outcome.REJECTED);
		assertThat(tools.trackerWasUnavailable()).isFalse();
	}

	@Test
	void searchNeedsAtLeastOneFilter() {
		TrackerTools tools = new TrackerTools(unreachable);

		assertThat(tools.searchTickets(null, " ").error()).contains("give a status, a sprint, or both");
	}

	@Test
	void springAiExposesExactlyTheFourReadOnlyToolsWithArgumentSchemas() {
		ToolCallback[] callbacks = ToolCallbacks.from(new TrackerTools(unreachable));

		assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
				.containsExactlyInAnyOrder("getTicket", "getTicketsForRequirement", "searchTickets", "getUatResults");
		assertThat(callbacks).filteredOn(c -> c.getToolDefinition().name().equals("getTicket")).singleElement()
				.satisfies(c -> assertThat(c.getToolDefinition().inputSchema()).contains("ticketId"));
	}

	@Test
	void aModelStyleJsonCallWithABadIdIsRejectedThroughSpringAi() {
		TrackerTools tools = new TrackerTools(unreachable);
		ToolCallback getTicket = java.util.Arrays.stream(ToolCallbacks.from(tools))
				.filter(c -> c.getToolDefinition().name().equals("getTicket")).findFirst().orElseThrow();

		// This is how the model's tool call arrives: a JSON object of arguments.
		String result = getTicket.call("{\"ticketId\": \"LOG-1 OR 1=1\"}");

		assertThat(result).contains("Invalid argument");
		assertThat(tools.calls()).singleElement().extracting(ToolCallRecord::outcome).isEqualTo(Outcome.REJECTED);
	}

	@Test
	void callsPerQuestionAreCapped() {
		TrackerTools tools = new TrackerTools(unreachable);
		for (int i = 0; i < TrackerTools.MAX_CALLS; i++) {
			tools.getTicket("bad id");
		}

		TrackerTools.TicketResult extra = tools.getTicket("LOG-142");

		assertThat(extra.error()).contains("call limit");
		assertThat(tools.calls()).hasSize(TrackerTools.MAX_CALLS + 1).last()
				.extracting(ToolCallRecord::detail).isEqualTo("call limit reached");
	}

}
