package dev.akshita.speclens.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Wraps Spring AI's EmbeddingModel with the input format gemini-embedding-2 expects.
 * The model has no task_type parameter; instead the task is written into the text.
 * Documents and questions use different prefixes (asymmetric retrieval), which is how
 * the model was trained to match a short question to a longer passage. See docs/adr/0003.
 */
@Service
public class EmbeddingService {

	/** Gemini's batchEmbedContents accepts at most 100 texts per call. */
	static final int MAX_BATCH = 100;

	private final EmbeddingModel embeddingModel;

	public EmbeddingService(EmbeddingModel embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

	/** Embeds passages of one document, in order. {@code title} is the document name. */
	public List<float[]> embedPassages(String title, List<String> passages) {
		List<float[]> vectors = new ArrayList<>(passages.size());
		for (int from = 0; from < passages.size(); from += MAX_BATCH) {
			List<String> batch = passages.subList(from, Math.min(from + MAX_BATCH, passages.size()))
					.stream()
					.map(text -> documentInput(title, text))
					.toList();
			vectors.addAll(embeddingModel.embed(batch));
		}
		return vectors;
	}

	public float[] embedQuestion(String question) {
		return embeddingModel.embed(queryInput(question));
	}

	static String documentInput(String title, String text) {
		return "title: " + title + " | text: " + text;
	}

	static String queryInput(String question) {
		return "task: question answering | query: " + question;
	}

}
