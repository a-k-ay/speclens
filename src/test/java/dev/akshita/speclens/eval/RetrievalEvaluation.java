package dev.akshita.speclens.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import dev.akshita.speclens.TestcontainersConfiguration;
import dev.akshita.speclens.ai.EmbeddingService;
import dev.akshita.speclens.ask.AskResponse;
import dev.akshita.speclens.ask.AskService;
import dev.akshita.speclens.ask.Citation;
import dev.akshita.speclens.eval.EvalReport.AnswerableResult;
import dev.akshita.speclens.eval.EvalReport.UnanswerableResult;
import dev.akshita.speclens.ingest.IngestionService;
import dev.akshita.speclens.project.ProjectRepository;
import dev.akshita.speclens.retrieval.Candidate;
import dev.akshita.speclens.retrieval.HybridRetriever;
import dev.akshita.speclens.retrieval.RetrievalRepository;
import dev.akshita.speclens.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Measures SpecLens against eval/golden-set.json using the REAL Gemini models (key from
 * .env) and a fresh pgvector container. Not part of the normal build or CI.
 *
 * Run with: ./mvnw test -Peval
 * Writes:   docs/EVAL.md
 */
@Tag("eval")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RetrievalEvaluation {

	static final int K = 5;

	/** Pause between questions to stay under free-tier requests-per-minute limits. */
	static final long PAUSE_MS = Long.getLong("eval.pauseMs", 3000);

	@Autowired
	Environment env;

	@Autowired
	ProjectRepository projects;

	@Autowired
	IngestionService ingestion;

	@Autowired
	EmbeddingService embeddings;

	@Autowired
	RetrievalRepository searches;

	@Autowired
	HybridRetriever hybrid;

	@Autowired
	AskService askService;

	@Test
	void evaluate() throws Exception {
		Assumptions.assumeTrue(hasText(env.getProperty("spring.ai.google.genai.api-key")),
				"GEMINI_API_KEY is not set (.env); the evaluation needs the real models");

		GoldenSet golden = GoldenSet.load(Path.of("eval/golden-set.json"));
		long projectId = projects.create("Evaluation " + System.currentTimeMillis(), "Golden set run").id();
		for (String document : golden.documents()) {
			Path path = Path.of(document);
			ingestion.ingest(projectId, path.getFileName().toString(), Files.readAllBytes(path));
		}

		List<AnswerableResult> answerable = new ArrayList<>();
		for (GoldenSet.Answerable q : golden.answerable()) {
			answerable.add(evaluate(projectId, q));
			pause();
		}
		List<UnanswerableResult> unanswerable = new ArrayList<>();
		for (GoldenSet.Unanswerable q : golden.unanswerable()) {
			unanswerable.add(evaluate(projectId, q));
			pause();
		}

		EvalReport report = new EvalReport(answerable, unanswerable, K, settings());
		Files.writeString(Path.of("docs/EVAL.md"), report.toMarkdown());
		System.out.println(report.summaryLine());

		assertThat(answerable).hasSize(golden.answerable().size());
	}

	private AnswerableResult evaluate(long projectId, GoldenSet.Answerable q) {
		try {
			float[] vector = embeddings.embedQuestion(q.question());
			List<RetrievedChunk> fused = hybrid.retrieve(projectId, q.question(), vector);
			List<Candidate> hybridTop = fused.stream().map(RetrievedChunk::chunk).toList();
			List<Candidate> vectorTop = searches.vectorSearch(projectId, vector, K);
			List<Candidate> keywordTop = searches.keywordSearch(projectId, q.question(), vector, K);

			AskResponse response = askService.ask(projectId, q.question());
			boolean citedExpected = response.citations().stream()
					.anyMatch(c -> q.isExpected(c.documentName(), c.page()));
			boolean containsFacts = q.answerMustContain().stream()
					.allMatch(f -> response.answer().toLowerCase(Locale.ROOT).contains(f.toLowerCase(Locale.ROOT)));

			return new AnswerableResult(q, rank(hybridTop, q), rank(vectorTop, q), rank(keywordTop, q),
					topSimilarity(hybridTop), describe(hybridTop.isEmpty() ? null : hybridTop.getFirst()),
					response.answered(), citedExpected, containsFacts,
					response.citations().stream().map(RetrievalEvaluation::describe).toList(), response.answer(), null);
		}
		catch (RuntimeException ex) {
			return AnswerableResult.failed(q, ex.toString());
		}
	}

	private UnanswerableResult evaluate(long projectId, GoldenSet.Unanswerable q) {
		try {
			float[] vector = embeddings.embedQuestion(q.question());
			List<Candidate> hybridTop = hybrid.retrieve(projectId, q.question(), vector).stream()
					.map(RetrievedChunk::chunk).toList();
			AskResponse response = askService.ask(projectId, q.question());
			return new UnanswerableResult(q, topSimilarity(hybridTop),
					describe(hybridTop.isEmpty() ? null : hybridTop.getFirst()), !response.answered(),
					String.valueOf(response.refusalReason()), response.answer(), null);
		}
		catch (RuntimeException ex) {
			return new UnanswerableResult(q, 0, "", false, "", "", ex.toString());
		}
	}

	/** 1-based rank of the first expected (document, page) in the list, or null if absent. */
	static Integer rank(List<Candidate> results, GoldenSet.Answerable q) {
		for (int i = 0; i < Math.min(K, results.size()); i++) {
			if (q.isExpected(results.get(i).documentName(), results.get(i).pageNumber())) {
				return i + 1;
			}
		}
		return null;
	}

	static double topSimilarity(List<Candidate> results) {
		return results.stream().mapToDouble(Candidate::similarity).max().orElse(0);
	}

	static String describe(Candidate c) {
		return c == null ? "" : c.documentName() + " p." + c.pageNumber();
	}

	static String describe(Citation c) {
		return c.documentName() + " p." + c.page();
	}

	private List<String> settings() {
		Function<String, String> p = key -> key + " = " + env.getProperty(key, "(not set)");
		return List.of(
				p.apply("spring.ai.google.genai.chat.options.model"),
				p.apply("spring.ai.google.genai.embedding.text.options.model"),
				p.apply("spring.ai.google.genai.embedding.text.options.dimensions"),
				p.apply("speclens.chunking.size"),
				p.apply("speclens.chunking.overlap"),
				p.apply("speclens.retrieval.candidates"),
				p.apply("speclens.retrieval.top-k"),
				p.apply("speclens.retrieval.rrf-k"),
				p.apply("speclens.retrieval.min-similarity"));
	}

	private static void pause() throws InterruptedException {
		Thread.sleep(PAUSE_MS);
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

}
