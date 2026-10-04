package dev.akshita.speclens.tracker;

/**
 * One tracker tool call made while answering a question, shown in the UI's "Tool calls"
 * list and used by the routing evaluation.
 *
 * @param tool the tool name, e.g. getTicketsForRequirement
 * @param arguments the arguments as the model sent them
 * @param outcome how the call ended
 * @param detail a short summary, e.g. "3 tickets: LOG-142 Done, ..." or the error message
 */
public record ToolCallRecord(String tool, String arguments, Outcome outcome, String detail, long durationMs) {

	public enum Outcome {

		/** The tracker answered (possibly with an empty list). */
		OK,
		/** The ticket doesn't exist. */
		NOT_FOUND,
		/** An argument failed validation; no HTTP call was made. */
		REJECTED,
		/** The tracker was unreachable, timed out or returned a server error. */
		UNAVAILABLE

	}

}
