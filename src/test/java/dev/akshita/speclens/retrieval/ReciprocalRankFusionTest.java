package dev.akshita.speclens.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

	@Test
	void chunkFoundByBothSearchesBeatsChunkThatTopsOnlyOne() {
		// A is #1 by meaning only; B is #2 in both lists.
		List<Candidate> vector = List.of(c(1), c(2), c(3));
		List<Candidate> keyword = List.of(c(4), c(2), c(5));

		List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(vector, keyword, 60, 5);

		assertThat(fused.get(0).chunk().chunkId()).isEqualTo(2);
		assertThat(fused.get(0).vectorRank()).isEqualTo(2);
		assertThat(fused.get(0).keywordRank()).isEqualTo(2);
		assertThat(fused.get(0).rrfScore()).isCloseTo(2.0 / 62, within(1e-12));
	}

	@Test
	void scoreUsesOneOverKPlusRank() {
		List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(List.of(c(7)), List.of(), 60, 5);

		assertThat(fused).singleElement().satisfies(r -> {
			assertThat(r.rrfScore()).isCloseTo(1.0 / 61, within(1e-12));
			assertThat(r.vectorRank()).isEqualTo(1);
			assertThat(r.keywordRank()).isNull();
		});
	}

	@Test
	void keywordOnlyHitsStillMakeTheList() {
		// e.g. an exact requirement id like "BR-8.3" that embeddings rank poorly.
		List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(List.of(c(1)), List.of(c(9)), 60, 5);

		assertThat(fused).extracting(r -> r.chunk().chunkId()).containsExactlyInAnyOrder(1L, 9L);
	}

	@Test
	void keepsOnlyTopN() {
		List<Candidate> twenty = LongStream.rangeClosed(1, 20).mapToObj(ReciprocalRankFusionTest::c).toList();

		assertThat(ReciprocalRankFusion.fuse(twenty, twenty, 60, 5))
				.extracting(r -> r.chunk().chunkId()).containsExactly(1L, 2L, 3L, 4L, 5L);
	}

	@Test
	void tiesAreBrokenBySimilarityThenId() {
		Candidate lowSim = new Candidate(1, 1, "a.pdf", 1, "x", 0.40);
		Candidate highSim = new Candidate(2, 1, "a.pdf", 1, "y", 0.80);

		// Same rank in different lists -> identical RRF score.
		List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(List.of(lowSim), List.of(highSim), 60, 5);

		assertThat(fused).extracting(r -> r.chunk().chunkId()).containsExactly(2L, 1L);
	}

	@Test
	void emptyInputsGiveEmptyResult() {
		assertThat(ReciprocalRankFusion.fuse(List.of(), List.of(), 60, 5)).isEmpty();
	}

	private static Candidate c(long id) {
		return new Candidate(id, 1, "doc.pdf", 1, "chunk " + id, 0.5);
	}

}
