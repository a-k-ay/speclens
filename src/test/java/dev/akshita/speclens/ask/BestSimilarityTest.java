package dev.akshita.speclens.ask;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.akshita.speclens.retrieval.Candidate;
import dev.akshita.speclens.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

class BestSimilarityTest {

	@Test
	void usesTheHighestSimilarityNotTheTopRrfRank() {
		// RRF order can differ from similarity order (a keyword hit may rank first).
		List<RetrievedChunk> sources = List.of(chunk(1, 0.55), chunk(2, 0.71), chunk(3, 0.60));

		assertThat(AskService.bestSimilarity(sources)).isEqualTo(0.71);
	}

	@Test
	void noSourcesMeansZero() {
		assertThat(AskService.bestSimilarity(List.of())).isZero();
	}

	private static RetrievedChunk chunk(long id, double similarity) {
		return new RetrievedChunk(new Candidate(id, 1, "doc.pdf", 1, "text", similarity), 1, null, 0.01);
	}

}
