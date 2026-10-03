package dev.akshita.speclens.ask;

import java.util.List;

import dev.akshita.speclens.ai.AiUnavailableException;
import dev.akshita.speclens.ai.EmbeddingService;
import dev.akshita.speclens.project.ProjectNotFoundException;
import dev.akshita.speclens.project.ProjectRepository;
import dev.akshita.speclens.retrieval.HybridRetriever;
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
	private final ChatClient chat;

	public AskService(ProjectRepository projects, EmbeddingService embeddings, HybridRetriever retriever,
			ChatClient.Builder chatClientBuilder) {
		this.projects = projects;
		this.embeddings = embeddings;
		this.retriever = retriever;
		this.chat = chatClientBuilder.build();
	}

	public AskResponse ask(long projectId, String question) {
		projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
		String q = question.strip();

		List<RetrievedChunk> sources = retriever.retrieve(projectId, q, embedQuestion(q));
		if (sources.isEmpty()) {
			// Nothing to ground an answer in, so don't spend a model call.
			return AskResponse.refused(q);
		}

		String answer = generate(q, sources);
		if (CitationParser.isRefusal(answer)) {
			return AskResponse.refused(q);
		}
		List<Integer> cited = CitationParser.citedSourceIndexes(answer, sources.size());
		if (cited.isEmpty()) {
			// An answer that cites nothing can't be checked against the documents, so it is
			// treated as ungrounded and not shown.
			log.warn("Suppressed uncited answer for project {}: {}", projectId, answer);
			return AskResponse.refused(q);
		}
		return new AskResponse(q, answer.strip(), true, toCitations(cited, sources));
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
