package dev.akshita.speclens.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.akshita.speclens.tracker.TrackerModels.TestRun;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;

/**
 * Checks that an answer's tracker facts came from tool results:
 *
 * 1. every ticket key (LOG-n) and UAT test id (TC-X-nn) mentioned was returned by a tool;
 * 2. in every sentence that names a ticket, each status word ("done", "blocked", ...) is the
 *    real status of a ticket named in that sentence;
 * 3. "passed" / "failed" in a sentence that names a test matches that test's result.
 *
 * The answer is withheld if any check fails. It is deliberately simple: it catches a model
 * stating a status no tool returned, which is the failure that matters most here.
 */
final class TrackerAnswerCheck {

	static final Pattern TICKET = Pattern.compile("\\bLOG-\\d+\\b");
	static final Pattern TEST = Pattern.compile("\\bTC-[A-Z]-\\d{2}\\b");

	/** Status words as people write them, mapped to the tracker's canonical status. */
	private static final Map<Pattern, String> TICKET_STATUS_WORDS = Map.of(
			Pattern.compile("(?i)\\bdone\\b"), "Done",
			Pattern.compile("(?i)\\bin progress\\b"), "In Progress",
			Pattern.compile("(?i)\\bin review\\b"), "In Review",
			Pattern.compile("(?i)\\bblocked\\b"), "Blocked",
			Pattern.compile("(?i)\\bto do\\b"), "To Do");

	private static final Pattern PASSED = Pattern.compile("(?i)\\bpass(?:ed|es)?\\b");
	private static final Pattern FAILED = Pattern.compile("(?i)\\bfail(?:ed|s|ure)?\\b");

	private TrackerAnswerCheck() {
	}

	/** Problems found; empty means the tracker facts check out. */
	static List<String> problems(String answer, Map<String, Ticket> tickets, Map<String, TestRun> testRuns) {
		List<String> problems = new ArrayList<>();
		Set<String> knownTickets = new LinkedHashSet<>(tickets.keySet());
		testRuns.values().forEach(r -> {
			if (r.defect() != null) {
				knownTickets.add(r.defect());
			}
		});

		for (String key : find(TICKET, answer)) {
			if (!knownTickets.contains(key)) {
				problems.add("mentions " + key + ", which no tool returned");
			}
		}
		for (String test : find(TEST, answer)) {
			if (!testRuns.containsKey(test)) {
				problems.add("mentions " + test + ", which no tool returned");
			}
		}

		for (String sentence : sentences(answer)) {
			List<Ticket> named = find(TICKET, sentence).stream().map(tickets::get).filter(t -> t != null).toList();
			if (!named.isEmpty()) {
				for (var word : TICKET_STATUS_WORDS.entrySet()) {
					List<String> statuses = named.stream().map(Ticket::status).toList();
					check(sentence, word.getKey(), word.getValue(), statuses, keys(named), problems);
				}
			}
			List<TestRun> namedTests = find(TEST, sentence).stream().map(testRuns::get).filter(r -> r != null).toList();
			if (!namedTests.isEmpty()) {
				List<String> results = namedTests.stream().map(TestRun::status).toList();
				String tests = String.join("/", namedTests.stream().map(TestRun::testCase).toList());
				check(sentence, PASSED, "PASS", results, tests, problems);
				check(sentence, FAILED, "FAIL", results, tests, problems);
			}
		}
		return problems;
	}

	/**
	 * A plain claim ("LOG-142 is done") needs at least one named item with that status. A
	 * negated claim ("has not passed", "isn't done yet") contradicts the data only if every
	 * named item does have that status.
	 */
	private static void check(String sentence, Pattern word, String status, List<String> actual, String items,
			List<String> problems) {
		Matcher m = word.matcher(sentence);
		while (m.find()) {
			boolean negated = NEGATION.matcher(sentence.substring(Math.max(0, m.start() - 25), m.start())).find();
			boolean contradicted = negated ? actual.stream().allMatch(status::equalsIgnoreCase)
					: actual.stream().noneMatch(status::equalsIgnoreCase);
			if (contradicted) {
				problems.add("says " + (negated ? "not " : "") + status + " but " + items + " is "
						+ String.join("/", actual) + ": \"" + sentence.strip() + "\"");
				return;
			}
		}
	}

	/** "not", "n't", "never", "yet to", "no longer" shortly before a status word. */
	private static final Pattern NEGATION = Pattern.compile("(?i)(\\bnot\\b|n't\\b|\\bnever\\b|\\byet to\\b|\\bno longer\\b)[^.]*$");

	static List<String> find(Pattern pattern, String text) {
		Set<String> found = new LinkedHashSet<>();
		Matcher m = pattern.matcher(text.toUpperCase(Locale.ROOT));
		while (m.find()) {
			found.add(m.group());
		}
		return List.copyOf(found);
	}

	/** Sentences and bullet lines. Splits after ". " but not inside IDs like "BR-8.1". */
	private static List<String> sentences(String text) {
		return List.of(text.split("(?<=[.!?])\\s+(?=[A-Z\\[*-])|\\n+"));
	}

	private static String keys(List<Ticket> tickets) {
		return String.join("/", tickets.stream().map(Ticket::key).toList());
	}

}
