package dev.akshita.speclens.tracker;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalises every argument the model passes to a tracker tool, before any
 * HTTP call. The model can only ever ask for well-formed IDs, statuses and sprints; anything
 * else ("LOG-1; DROP", "../admin", "all tickets") is rejected with a reason it can read.
 */
public final class TrackerIds {

	private static final Pattern TICKET = Pattern.compile("LOG-\\d{1,5}");
	private static final Pattern REQUIREMENT = Pattern.compile("(BR|NFR)-\\d{1,2}(\\.\\d{1,2})?");
	private static final Pattern CHANGE_REQUEST = Pattern.compile("CR-?0*(\\d{1,3})");
	private static final Pattern SPRINT = Pattern.compile("(?:SPRINT\\s*)?(\\d{1,3})");
	private static final List<String> STATUSES = List.of("To Do", "In Progress", "In Review", "Blocked", "Done");

	/** Marker for "the current sprint", resolved by asking the tracker. */
	public static final String CURRENT_SPRINT = "CURRENT";

	private TrackerIds() {
	}

	/** "log-142" becomes "LOG-142". */
	public static String ticket(String raw) {
		String value = clean(raw);
		if (!TICKET.matcher(value).matches()) {
			throw new InvalidToolArgumentException("'" + raw + "' is not a ticket key like LOG-142");
		}
		return value;
	}

	/** "br-8.1" becomes "BR-8.1"; "CR-3" and "cr 003" become "CR-003". */
	public static String requirement(String raw) {
		String value = clean(raw).replace(' ', '-');
		if (REQUIREMENT.matcher(value).matches()) {
			return value;
		}
		Matcher cr = CHANGE_REQUEST.matcher(value);
		if (cr.matches()) {
			return "CR-%03d".formatted(Integer.parseInt(cr.group(1)));
		}
		throw new InvalidToolArgumentException("'" + raw + "' is not a requirement ID like BR-8.1, NFR-3 or CR-003");
	}

	/** Case-insensitive match to one of the tracker's statuses, returned in its canonical form. */
	public static String status(String raw) {
		String value = raw == null ? "" : raw.strip();
		return STATUSES.stream().filter(s -> s.equalsIgnoreCase(value)).findFirst()
				.orElseThrow(() -> new InvalidToolArgumentException(
						"'" + raw + "' is not a status; use one of " + String.join(", ", STATUSES)));
	}

	/** "Sprint 9", "sprint 9" or "9" become "Sprint 9"; "current" or "this sprint" become CURRENT_SPRINT. */
	public static String sprint(String raw) {
		String value = clean(raw);
		if (value.equals("CURRENT") || value.equals("THIS SPRINT") || value.equals("CURRENT SPRINT")) {
			return CURRENT_SPRINT;
		}
		Matcher m = SPRINT.matcher(value);
		if (m.matches()) {
			return "Sprint " + Integer.parseInt(m.group(1));
		}
		throw new InvalidToolArgumentException("'" + raw + "' is not a sprint like 'Sprint 9' or 'current'");
	}

	private static String clean(String raw) {
		return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
	}

	/** A tool argument that fails validation; the model receives the message, no call is made. */
	public static class InvalidToolArgumentException extends RuntimeException {

		public InvalidToolArgumentException(String message) {
			super(message);
		}

	}

}
