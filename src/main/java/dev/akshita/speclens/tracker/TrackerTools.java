package dev.akshita.speclens.tracker;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.akshita.speclens.tracker.ToolCallRecord.Outcome;
import dev.akshita.speclens.tracker.TrackerIds.InvalidToolArgumentException;
import dev.akshita.speclens.tracker.TrackerModels.TestRun;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The read-only tracker tools the model may call. A new instance is created for every
 * question, so it can record each call for the UI and the evaluation.
 *
 * Safety: no tool changes anything; every argument is validated before an HTTP call; at most
 * {@link #MAX_CALLS} calls per question; failures come back as an {@code error} the model can
 * read, never as invented data. See docs/adr/0012.
 */
public class TrackerTools {

	public static final int MAX_CALLS = 6;
	static final String UNAVAILABLE_MESSAGE = "TRACKER_UNAVAILABLE: live tracker data is unavailable right now.";

	public record TicketResult(boolean found, Ticket ticket, String error) {
	}

	public record TicketsResult(int count, List<Ticket> tickets, String error) {
	}

	public record TestRunsResult(int count, List<TestRun> testRuns, String error) {
	}

	private final TrackerClient client;
	private final List<ToolCallRecord> calls = new CopyOnWriteArrayList<>();

	public TrackerTools(TrackerClient client) {
		this.client = client;
	}

	@Tool(name = "getTicket", description = "Get one ticket from the project tracker by its key, e.g. LOG-142. "
			+ "Returns its summary, status, sprint, assignee and the requirement IDs it implements.")
	public TicketResult getTicket(@ToolParam(description = "Ticket key such as LOG-142") String ticketId) {
		return run("getTicket", "ticketId=" + ticketId, () -> {
			var ticket = client.ticket(TrackerIds.ticket(ticketId));
			return ticket.map(t -> new Result<>(new TicketResult(true, t, null), Outcome.OK, describe(t)))
					.orElseGet(() -> new Result<>(new TicketResult(false, null, "No ticket " + ticketId),
							Outcome.NOT_FOUND, "not found"));
		}, error -> new TicketResult(false, null, error));
	}

	@Tool(name = "getTicketsForRequirement", description = "List every tracker ticket (stories and bugs) linked to "
			+ "a requirement or change request, e.g. BR-8.1 or CR-003. An empty list means no ticket exists for it.")
	public TicketsResult getTicketsForRequirement(
			@ToolParam(description = "Requirement ID such as BR-8.1, NFR-4 or CR-003") String requirementId) {
		return run("getTicketsForRequirement", "requirementId=" + requirementId, () -> {
			List<Ticket> tickets = client.search(TrackerIds.requirement(requirementId), null, null);
			return new Result<>(new TicketsResult(tickets.size(), tickets, null), Outcome.OK, describe(tickets));
		}, error -> new TicketsResult(0, List.of(), error));
	}

	@Tool(name = "searchTickets", description = "Find tracker tickets by status and/or sprint, e.g. all Blocked "
			+ "tickets in the current sprint. Give at least one filter.")
	public TicketsResult searchTickets(
			@ToolParam(description = "One of: To Do, In Progress, In Review, Blocked, Done", required = false) String status,
			@ToolParam(description = "A sprint such as 'Sprint 9', or 'current' for this sprint", required = false) String sprint) {
		return run("searchTickets", "status=" + status + ", sprint=" + sprint, () -> {
			if (blank(status) && blank(sprint)) {
				throw new InvalidToolArgumentException("give a status, a sprint, or both");
			}
			String s = blank(status) ? null : TrackerIds.status(status);
			String sp = blank(sprint) ? null : TrackerIds.sprint(sprint);
			if (TrackerIds.CURRENT_SPRINT.equals(sp)) {
				sp = client.project().currentSprint();
			}
			List<Ticket> tickets = client.search(null, s, sp);
			return new Result<>(new TicketsResult(tickets.size(), tickets, null), Outcome.OK, describe(tickets));
		}, error -> new TicketsResult(0, List.of(), error));
	}

	@Tool(name = "getUatResults", description = "Get UAT test results (PASS, FAIL or NOT_RUN, with any defect ticket) "
			+ "for a requirement or change request, e.g. BR-8.1 or CR-003.")
	public TestRunsResult getUatResults(
			@ToolParam(description = "Requirement ID such as BR-8.1 or CR-003") String requirementId) {
		return run("getUatResults", "requirementId=" + requirementId, () -> {
			List<TestRun> runs = client.testRuns(TrackerIds.requirement(requirementId));
			String detail = runs.isEmpty() ? "no UAT runs"
					: runs.stream().map(r -> r.testCase() + " " + r.status()).collect(Collectors.joining(", "));
			return new Result<>(new TestRunsResult(runs.size(), runs, null), Outcome.OK, detail);
		}, error -> new TestRunsResult(0, List.of(), error));
	}

	/** Every call made so far, in order. */
	public List<ToolCallRecord> calls() {
		return List.copyOf(calls);
	}

	/** True if any call found the tracker down, so the answer can say live data is missing. */
	public boolean trackerWasUnavailable() {
		return calls.stream().anyMatch(c -> c.outcome() == Outcome.UNAVAILABLE);
	}

	// ---- shared handling: limit, validation, failures, logging ----

	private record Result<T>(T value, Outcome outcome, String detail) {
	}

	private <T> T run(String tool, String arguments, Supplier<Result<T>> call, Function<String, T> onError) {
		long start = System.nanoTime();
		if (calls.size() >= MAX_CALLS) {
			return record(tool, arguments, Outcome.REJECTED, "call limit reached", start,
					onError.apply("Tool call limit of " + MAX_CALLS + " reached; answer with what you have."));
		}
		try {
			Result<T> result = call.get();
			return record(tool, arguments, result.outcome(), result.detail(), start, result.value());
		}
		catch (InvalidToolArgumentException ex) {
			return record(tool, arguments, Outcome.REJECTED, ex.getMessage(), start,
					onError.apply("Invalid argument: " + ex.getMessage()));
		}
		catch (TrackerUnavailableException ex) {
			return record(tool, arguments, Outcome.UNAVAILABLE, "tracker unavailable", start,
					onError.apply(UNAVAILABLE_MESSAGE));
		}
	}

	private <T> T record(String tool, String arguments, Outcome outcome, String detail, long start, T value) {
		calls.add(new ToolCallRecord(tool, arguments, outcome, detail, (System.nanoTime() - start) / 1_000_000));
		return value;
	}

	private static String describe(Ticket t) {
		return t.key() + " " + t.status();
	}

	private static String describe(List<Ticket> tickets) {
		return tickets.isEmpty() ? "no tickets"
				: tickets.size() + " ticket(s): " + tickets.stream().map(TrackerTools::describe)
						.collect(Collectors.joining(", "));
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank() || s.equalsIgnoreCase("null");
	}

}
