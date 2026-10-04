package dev.akshita.speclens.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.akshita.speclens.HttpIntegrationTest;
import dev.akshita.speclens.tracker.ToolCallRecord.Outcome;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * The tools calling the mock tracker over real HTTP: the app starts its web server on a
 * random port and TrackerClient reaches /mock-tracker through it, exactly as in production.
 */
@HttpIntegrationTest
class TrackerToolsHttpTest {

	@Autowired
	TrackerClient client;

	@Test
	void getTicketReturnsTheTicketFromTheTracker() {
		TrackerTools tools = new TrackerTools(client);

		TrackerTools.TicketResult result = tools.getTicket("log-142");

		assertThat(result.found()).isTrue();
		assertThat(result.ticket().status()).isEqualTo("Done");
		assertThat(result.ticket().requirements()).containsExactly("BR-8.1");
		assertThat(tools.calls()).singleElement().satisfies(c -> {
			assertThat(c.outcome()).isEqualTo(Outcome.OK);
			assertThat(c.detail()).isEqualTo("LOG-142 Done");
		});
	}

	@Test
	void unknownTicketIsNotFoundRatherThanAnError() {
		TrackerTools tools = new TrackerTools(client);

		assertThat(tools.getTicket("LOG-999").found()).isFalse();
		assertThat(tools.calls()).singleElement().extracting(ToolCallRecord::outcome).isEqualTo(Outcome.NOT_FOUND);
	}

	@Test
	void ticketsForTheChangeRequestUseTheNormalisedId() {
		TrackerTools tools = new TrackerTools(client);

		// The model may write "CR-3"; the tracker labels it CR-003.
		TrackerTools.TicketsResult result = tools.getTicketsForRequirement("CR-3");

		assertThat(result.tickets()).extracting(Ticket::key).containsExactlyInAnyOrder("LOG-171", "LOG-190");
	}

	@Test
	void requirementWithNoTicketsGivesAnEmptyListNotAnError() {
		TrackerTools tools = new TrackerTools(client);

		TrackerTools.TicketsResult result = tools.getTicketsForRequirement("BR-8.4");

		assertThat(result.count()).isZero();
		assertThat(result.error()).isNull();
	}

	@Test
	void blockedTicketsInTheCurrentSprintResolveTheSprintFromTheTracker() {
		TrackerTools tools = new TrackerTools(client);

		TrackerTools.TicketsResult result = tools.searchTickets("blocked", "this sprint");

		assertThat(result.tickets()).extracting(Ticket::key).containsExactlyInAnyOrder("LOG-106", "LOG-146");
		assertThat(result.tickets()).allSatisfy(t -> assertThat(t.sprint()).isEqualTo("Sprint 9"));
	}

	@Test
	void uatResultsShowTheFailedTestAndItsDefect() {
		TrackerTools tools = new TrackerTools(client);

		TrackerTools.TestRunsResult result = tools.getUatResults("BR-8.1");

		assertThat(result.testRuns()).singleElement().satisfies(r -> {
			assertThat(r.testCase()).isEqualTo("TC-I-01");
			assertThat(r.status()).isEqualTo("FAIL");
			assertThat(r.defect()).isEqualTo("LOG-190");
		});
		assertThat(tools.calls()).singleElement().extracting(ToolCallRecord::detail).isEqualTo("TC-I-01 FAIL");
	}

}
