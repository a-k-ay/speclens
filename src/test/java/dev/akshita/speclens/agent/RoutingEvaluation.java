package dev.akshita.speclens.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.akshita.speclens.TestcontainersConfiguration;
import dev.akshita.speclens.ask.RefusalReason;
import dev.akshita.speclens.ingest.IngestionService;
import dev.akshita.speclens.mocktracker.MockTrackerData;
import dev.akshita.speclens.project.ProjectRepository;
import dev.akshita.speclens.tracker.ToolCallRecord;
import dev.akshita.speclens.tracker.TrackerModels.TestRun;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Measures intent routing against eval/routing-set.json with the REAL models, a fresh
 * pgvector container and the mock tracker reached over HTTP (random port). Not run in CI.
 *
 * Run with: ./mvnw test -Peval -Dtest=RoutingEvaluation
 * Writes:   docs/EVAL-ROUTING.md and src/main/resources/static/eval-routing-summary.json
 */
@Tag("eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RoutingEvaluation {

	static final long PAUSE_MS = Long.getLong("eval.pauseMs", 8000);

	private static final Pattern HONEST_MISSING = Pattern.compile(
			"(?i)not found|does ?n[o']t exist|no ticket|couldn't find|could not find|no matching|no such|isn't in|is not in");

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RoutingSet(String description, List<Question> questions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Question(String id, String question, Intent expectedIntent, List<String> expectedCalls,
			List<String> mustMention, List<String> mustMentionAny, Boolean honestMissing) {

		Question {
			expectedCalls = expectedCalls == null ? List.of() : expectedCalls;
			mustMention = mustMention == null ? List.of() : mustMention;
			mustMentionAny = mustMentionAny == null ? List.of() : mustMentionAny;
			honestMissing = Boolean.TRUE.equals(honestMissing);
		}

		boolean checksCalls() {
			return !expectedCalls.isEmpty();
		}

		boolean checksFacts() {
			return !mustMention.isEmpty() || !mustMentionAny.isEmpty();
		}

	}

	/** One question's outcome. Booleans are null when that check doesn't apply. */
	record Result(Question q, AgentResponse r, Boolean intentOk, Boolean callsOk, Boolean factsOk, Boolean honestOk,
			boolean trackerAnswer, List<String> inventedProblems, String error) {
	}

	@Autowired
	Environment env;

	@Autowired
	ProjectRepository projects;

	@Autowired
	IngestionService ingestion;

	@Autowired
	AgentService agent;

	@Autowired
	MockTrackerData trackerData;

	@Test
	void evaluate() throws Exception {
		String key = env.getProperty("spring.ai.google.genai.api-key");
		Assumptions.assumeTrue(key != null && !key.isBlank(), "GEMINI_API_KEY is not set (.env)");

		RoutingSet set = JsonMapper.builder().build().readValue(Path.of("eval/routing-set.json").toFile(),
				RoutingSet.class);
		long projectId = projects.create("Routing evaluation " + System.currentTimeMillis(), null).id();
		try (var files = Files.list(Path.of("samples"))) {
			for (Path file : files.filter(f -> f.toString().matches(".*\\.(pdf|docx)$")).sorted().toList()) {
				ingestion.ingest(projectId, file.getFileName().toString(), Files.readAllBytes(file));
			}
		}

		List<Result> results = new ArrayList<>();
		for (Question q : set.questions()) {
			results.add(evaluate(projectId, q));
			Thread.sleep(PAUSE_MS);
		}

		RoutingReport report = new RoutingReport(results);
		Files.writeString(Path.of("docs/EVAL-ROUTING.md"), report.toMarkdown());
		Files.writeString(Path.of("src/main/resources/static/eval-routing-summary.json"),
				JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build()
						.writeValueAsString(report.summary()));
		System.out.println(report.summaryLine());
		assertThat(results).hasSize(set.questions().size());
	}

	private Result evaluate(long projectId, Question q) {
		AgentResponse r;
		try {
			r = askWaitingOutRateLimits(projectId, q.question());
		}
		catch (RuntimeException ex) {
			Throwable root = rootCause(ex);
			return new Result(q, null, false, q.checksCalls() ? false : null, q.checksFacts() ? false : null,
					q.honestMissing() ? false : null, false, List.of(), root.getClass().getSimpleName() + ": "
							+ String.valueOf(root.getMessage()));
		}
		String answer = r.answer().toLowerCase(Locale.ROOT);
		List<String> calls = r.toolCalls().stream().map(c -> c.tool() + "(" + c.arguments() + ")").toList();

		Boolean callsOk = !q.checksCalls() ? null
				: q.expectedCalls().stream().allMatch(expected -> calls.stream().anyMatch(c -> c.contains(expected)));
		Boolean factsOk = !q.checksFacts() ? null
				: r.answered() && q.mustMention().stream().allMatch(f -> answer.contains(f.toLowerCase(Locale.ROOT)))
						&& (q.mustMentionAny().isEmpty()
								|| q.mustMentionAny().stream().anyMatch(f -> answer.contains(f.toLowerCase(Locale.ROOT))));
		Boolean honestOk = !q.honestMissing() ? null : !r.answered() || HONEST_MISSING.matcher(r.answer()).find();

		boolean trackerAnswer = r.answered() && !r.toolCalls().isEmpty();
		List<String> invented = trackerAnswer ? inventedAgainstGroundTruth(r) : List.of();
		return new Result(q, r, r.intent() == q.expectedIntent(), callsOk, factsOk, honestOk, trackerAnswer, invented,
				null);
	}

	/**
	 * The free tier allows 15 model requests a minute and one question makes 2 to 4, so a burst
	 * can hit HTTP 429. That says nothing about routing, so wait it out once and retry.
	 */
	private AgentResponse askWaitingOutRateLimits(long projectId, String question) {
		try {
			return agent.ask(projectId, question);
		}
		catch (RuntimeException ex) {
			if (!String.valueOf(rootCause(ex).getMessage()).contains("429")) {
				throw ex;
			}
			try {
				Thread.sleep(45_000);
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
			return agent.ask(projectId, question);
		}
	}

	private static Throwable rootCause(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root;
	}

	/**
	 * Re-checks a shown answer against the FULL tracker data, not just what the tools returned,
	 * so this is an independent audit of the guard in AgentService.
	 */
	private List<String> inventedAgainstGroundTruth(AgentResponse r) {
		var data = trackerData.dataset();
		Map<String, Ticket> tickets = data.issues().stream().collect(Collectors.toMap(i -> i.key(),
				i -> new Ticket(i.key(), i.type(), i.summary(), i.status(), i.sprint(), i.assignee(), i.priority(),
						i.requirements(), i.updated(), i.description())));
		Map<String, TestRun> runs = data.testRuns().stream().collect(Collectors.toMap(t -> t.testCase(),
				t -> new TestRun(t.testCase(), t.title(), t.requirements(), t.status(), t.executedOn(), t.executedBy(),
						t.defect(), t.comment())));
		Set<String> missing = r.toolCalls().stream()
				.filter(c -> c.outcome() == ToolCallRecord.Outcome.NOT_FOUND)
				.map(c -> c.arguments().replaceAll(".*=", "").toUpperCase(Locale.ROOT))
				.collect(Collectors.toSet());
		return TrackerAnswerCheck.problems(r.answer(), tickets, runs, missing);
	}

	// ---- report ----

	record RoutingReport(List<Result> results) {

		long count(Function<Result, Boolean> check) {
			return results.stream().map(check).filter(Boolean.TRUE::equals).count();
		}

		long applicable(Function<Result, Boolean> check) {
			return results.stream().map(check).filter(b -> b != null).count();
		}

		long trackerAnswers() {
			return results.stream().filter(Result::trackerAnswer).count();
		}

		long inventedShown() {
			return results.stream().filter(r -> !r.inventedProblems().isEmpty()).count();
		}

		long withheld() {
			return results.stream().filter(r -> r.r() != null
					&& r.r().refusalReason() == RefusalReason.UNVERIFIED_TRACKER_DATA).count();
		}

		String summaryLine() {
			return "ROUTING intent %d/%d, tracker calls %d/%d, exact facts %d/%d, invented shown %d of %d tracker answers"
					.formatted(count(Result::intentOk), results.size(), count(Result::callsOk), applicable(Result::callsOk),
							count(Result::factsOk), applicable(Result::factsOk), inventedShown(), trackerAnswers());
		}

		Map<String, Object> summary() {
			Map<String, Object> s = new java.util.LinkedHashMap<>();
			s.put("generated", java.time.LocalDate.now().toString());
			s.put("questions", results.size());
			s.put("intentCorrect", count(Result::intentOk));
			s.put("callsCorrect", count(Result::callsOk));
			s.put("callsChecked", applicable(Result::callsOk));
			s.put("factsCorrect", count(Result::factsOk));
			s.put("factsChecked", applicable(Result::factsOk));
			s.put("honestMissing", count(Result::honestOk));
			s.put("honestMissingChecked", applicable(Result::honestOk));
			s.put("trackerAnswers", trackerAnswers());
			s.put("inventedShown", inventedShown());
			s.put("withheldByCheck", withheld());
			return s;
		}

		String toMarkdown() {
			StringBuilder md = new StringBuilder();
			md.append("# SpecLens routing evaluation (v2)\n\n")
					.append("Generated by `RoutingEvaluation` (`./mvnw test -Peval -Dtest=RoutingEvaluation`) on ")
					.append(java.time.LocalDate.now()).append(" with the real Gemini models, the six sample documents ")
					.append("and the mock tracker called over HTTP. Labelled set: ")
					.append("[`eval/routing-set.json`](../eval/routing-set.json), ").append(results.size())
					.append(" questions. Document retrieval is measured separately in [EVAL.md](EVAL.md).\n\n");

			md.append("## Headline\n\n| Metric | Result |\n|---|---|\n")
					.append(row("Intent classified correctly", count(Result::intentOk) + "/" + results.size()))
					.append(row("Expected tracker calls made", count(Result::callsOk) + "/" + applicable(Result::callsOk)))
					.append(row("Answer states the tracker facts exactly", count(Result::factsOk) + "/" + applicable(Result::factsOk)))
					.append(row("Honest about a ticket that doesn't exist", count(Result::honestOk) + "/" + applicable(Result::honestOk)))
					.append(row("Answers shown with an invented ticket or status", inventedShown() + " of " + trackerAnswers()
							+ " tracker answers"))
					.append(row("Answers withheld by the status check", String.valueOf(withheld())))
					.append("\n");

			md.append("## Intent: expected (rows) vs classified (columns)\n\n| | ");
			for (Intent i : Intent.values()) {
				md.append(i).append(" | ");
			}
			md.append("\n|---|").append("---|".repeat(Intent.values().length)).append('\n');
			for (Intent expected : Intent.values()) {
				md.append("| **").append(expected).append("** | ");
				for (Intent got : Intent.values()) {
					long n = results.stream().filter(r -> r.q().expectedIntent() == expected && r.r() != null
							&& r.r().intent() == got).count();
					md.append(n == 0 ? "" : String.valueOf(n)).append(" | ");
				}
				md.append('\n');
			}

			md.append("\n## Per question\n\n")
					.append("| ID | Question | Expected | Classified (conf.) | Route | Calls ok | Facts ok | Answered |\n")
					.append("|---|---|---|---|---|---|---|---|\n");
			for (Result r : results) {
				md.append("| ").append(r.q().id()).append(" | ").append(cell(r.q().question())).append(" | ")
						.append(r.q().expectedIntent()).append(" | ")
						.append(r.r() == null ? "ERROR" : r.r().intent() + " (" + "%.2f".formatted(r.r().intentConfidence()) + ")"
								+ (Boolean.TRUE.equals(r.intentOk()) ? "" : " **x**"))
						.append(" | ").append(r.r() == null ? "" : r.r().route())
						.append(" | ").append(tick(r.callsOk() != null ? r.callsOk() : r.honestOk()))
						.append(" | ").append(tick(r.factsOk()))
						.append(" | ").append(r.r() == null ? "" : r.r().answered() ? "yes" : "refused: " + r.r().refusalReason())
						.append(" |\n");
			}

			md.append("\n## Tool calls and answers\n\n");
			for (Result r : results) {
				md.append("**").append(r.q().id()).append("** ").append(cell(r.q().question())).append("<br>");
				if (r.error() != null) {
					md.append("ERROR: ").append(cell(r.error())).append("\n\n");
					continue;
				}
				for (ToolCallRecord c : r.r().toolCalls()) {
					md.append("`").append(c.tool()).append("(").append(c.arguments()).append(")` → ").append(c.outcome())
							.append(": ").append(cell(c.detail())).append("<br>");
				}
				md.append(cell(r.r().answer()));
				if (!r.inventedProblems().isEmpty()) {
					md.append("<br>**Invented (ground truth):** ").append(cell(String.join("; ", r.inventedProblems())));
				}
				md.append("\n\n");
			}

			md.append("## Method\n\n")
					.append("1. A fresh Postgres + pgvector container; the six sample documents are ingested; the mock ")
					.append("tracker is reached over HTTP through the app's own random port.\n")
					.append("2. Each question goes through the full `/ask` pipeline: classification, routing, tools, answer, checks.\n")
					.append("3. **Intent** compares the classifier's intent with the label (a low-confidence fallback still ")
					.append("counts the classifier's own intent).\n")
					.append("4. **Tracker calls** pass when every expected call appears in the tool-call log; a misrouted ")
					.append("question that never reaches the tracker fails this check.\n")
					.append("5. **Exact facts** pass when the answer was shown and contains every expected fact from the ")
					.append("tracker data.\n")
					.append("6. **Invented** re-checks every shown tracker answer against the full tracker data (not ")
					.append("just what the tools returned): unknown tickets or tests, or a status that contradicts the ")
					.append("ticket named in the same sentence.\n")
					.append("7. Model output varies a little between runs; the labels are fixed.\n");
			return md.toString();
		}

		private static String row(String metric, String value) {
			return "| " + metric + " | **" + value + "** |\n";
		}

		private static String tick(Boolean b) {
			return b == null ? "" : b ? "yes" : "**no**";
		}

		private static String cell(String text) {
			return text == null ? "" : text.replace("|", "\\|").replaceAll("\\s+", " ").strip();
		}

	}

}
