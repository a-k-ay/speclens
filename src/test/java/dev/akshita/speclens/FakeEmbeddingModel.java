package dev.akshita.speclens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic stand-in for Gemini embeddings, used by every test (CI has no API key).
 * Each word is hashed into one of 768 buckets and the vector is L2-normalised, so texts
 * that share words have high cosine similarity. Crude, but enough to test retrieval logic.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

	public static final int DIMENSIONS = 768;

	private int calls;

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		calls++;
		List<Embedding> results = new ArrayList<>();
		List<String> texts = request.getInstructions();
		for (int i = 0; i < texts.size(); i++) {
			results.add(new Embedding(vectorFor(texts.get(i)), i));
		}
		return new EmbeddingResponse(results);
	}

	@Override
	public float[] embed(Document document) {
		return vectorFor(document.getText());
	}

	@Override
	public int dimensions() {
		return DIMENSIONS;
	}

	/** Number of API calls made, so tests can check batching. */
	public int calls() {
		return calls;
	}

	public static float[] vectorFor(String input) {
		// Drop the Gemini task prefix ("title: x | text: ..." / "task: y | query: ...").
		String text = input.substring(input.lastIndexOf('|') + 1).replaceFirst("^\\s*(text|query):", "");
		float[] vector = new float[DIMENSIONS];
		for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
			if (!word.isEmpty()) {
				vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
			}
		}
		double norm = 0;
		for (float v : vector) {
			norm += v * v;
		}
		if (norm == 0) {
			vector[0] = 1f;
			return vector;
		}
		float scale = (float) (1 / Math.sqrt(norm));
		for (int i = 0; i < DIMENSIONS; i++) {
			vector[i] *= scale;
		}
		return vector;
	}

}
