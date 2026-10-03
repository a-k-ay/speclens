package dev.akshita.speclens.ask;

import java.util.List;

import dev.akshita.speclens.ai.AiUnavailableException;
import dev.akshita.speclens.ai.EmbeddingService;
import dev.akshita.speclens.project.ProjectNotFoundException;
import dev.akshita.speclens.project.ProjectRepository;
import dev.akshita.speclens.retrieval.HybridRetriever;
import dev.akshita.speclens.retrieval.RetrievalProperties;
import dev.akshita.speclens.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Question -> hybrid retrieval -> grounded prompt -> answer with citations. */
@Service
public class AskService {

	private static final Logger log = LoggerFactory.getLogger(AskService.class);

	private final ProjectRepository projects;
	private final EmbeddingService embeddings;
	private final HybridRetriever retriever;
	private final RetrievalProperties retrievalProperties;
	private final ChatClient chat;

	public AskService(ProjectRepository projects, EmbeddingService embeddings, HybridRetriever retriever,
			RetrievalProperties retrievalProperties, ChatClient.Builder chatClientBuilder) {
		this.projects = projects;
		this.embeddings = embeddings;
		this.retriever = retriever;
		this.retrievalProperties = retrievalProperties;
		this.chat = chatClientBuilder.build();
	}

	public AskResponse ask(long projectId, String question) {
		projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
		String q = question.strip();

		List<RetrievedChunk> sources = retriever.retrieve(projectId, q, embedQuestion(q));

		// Layer 1: nothing close enough to the question, so don't spend a model call.
		double best = bestSimilarity(sources);
		if (best < retrievalProperties.minSimilarity() || sources.isEmpty()) {
			log.info("Refused before model call: best similarity {} < {} (project {})", best,
					retrievalProperties.minSimilarity(), projectId);
			return AskResponse.refused(q, RefusalReason.NO_RELEVANT_SOURCES);
		}

		// Layer 2: the model read the sources and said the answer isn't there.
		String answer = generate(q, sources);
		if (CitationParser.isRefusal(answer)) {
			return AskResponse.refused(q, RefusalReason.NOT_IN_SOURCES);
		}

		// Layer 3: an answer that cites no real source can't be checked against the
		// documents, so it is withheld.
		List<Integer> cited = CitationParser.citedSourceIndexes(answer, sources.size());
		if (cited.isEmpty()) {
			log.warn("Suppressed uncited answer for project {}: {}", projectId, answer);
			return AskResponse.refused(q, RefusalReason.UNGROUNDED_ANSWER);
		}
		return AskResponse.answered(q, answer.strip(), toCitations(cited, sources));
	}

	/** Highest cosine similarity between the question and any retrieved source (0 if none). */
	static double bestSimilarity(List<RetrievedChunk> sources) {
		return sources.stream().mapToDouble(s -> s.chunk().similarity()).max().orElse(0);
	}

	private float[] embedQuestion(String question) {
		try {
			return embeddings.embedQuestion(question);
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException("Embedding service failed; please try again", ex);
		}
	}

	private String generate(String question, List<RetrievedChunk> sources) {
		String answer;
		try {
			answer = chat.prompt()
					.system(GroundedPrompt.SYSTEM)
					.user(GroundedPrompt.userMessage(question, sources))
					.call()
					.content();
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException("Answer generation failed; please try again", ex);
		}
		if (answer == null || answer.isBlank()) {
			throw new AiUnavailableException("The model returned an empty answer", null);
		}
		return answer;
	}

	private static List<Citation> toCitations(List<Integer> cited, List<RetrievedChunk> sources) {
		return cited.stream().map(i -> {
			var chunk = sources.get(i).chunk();
			return new Citation(GroundedPrompt.sourceId(i), chunk.documentId(), chunk.documentName(),
					chunk.pageNumber(), chunk.content(), chunk.similarity());
		}).toList();
	}

}
