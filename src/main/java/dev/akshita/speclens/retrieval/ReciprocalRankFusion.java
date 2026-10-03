package dev.akshita.speclens.retrieval;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges the vector and keyword result lists with Reciprocal Rank Fusion:
 *
 * <pre>score(chunk) = sum over lists of 1 / (k + rank)</pre>
 *
 * Only ranks are used, never raw scores, because cosine similarity (0..1) and
 * ts_rank_cd (unbounded) are on different scales. A chunk that both searches rank
 * highly beats one that only a single search likes. k (60 in the original paper,
 * Cormack et al. 2009) damps the gap between rank 1 and rank 2. See docs/adr/0005.
 */
public final class ReciprocalRankFusion {

	private ReciprocalRankFusion() {
	}

	public static List<RetrievedChunk> fuse(List<Candidate> vectorResults, List<Candidate> keywordResults, int k,
			int topN) {
		Map<Long, Accumulator> byChunk = new LinkedHashMap<>();
		for (int i = 0; i < vectorResults.size(); i++) {
			Accumulator acc = accumulatorFor(byChunk, vectorResults.get(i));
			acc.vectorRank = i + 1;
			acc.score += 1.0 / (k + i + 1);
		}
		for (int i = 0; i < keywordResults.size(); i++) {
			Accumulator acc = accumulatorFor(byChunk, keywordResults.get(i));
			acc.keywordRank = i + 1;
			acc.score += 1.0 / (k + i + 1);
		}
		return byChunk.values().stream()
				.map(a -> new RetrievedChunk(a.chunk, a.vectorRank, a.keywordRank, a.score))
				.sorted(Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed()
						// Ties: prefer the more semantically similar chunk, then a stable order.
						.thenComparing(r -> r.chunk().similarity(), Comparator.reverseOrder())
						.thenComparingLong(r -> r.chunk().chunkId()))
				.limit(topN)
				.toList();
	}

	private static Accumulator accumulatorFor(Map<Long, Accumulator> byChunk, Candidate candidate) {
		return byChunk.computeIfAbsent(candidate.chunkId(), id -> new Accumulator(candidate));
	}

	private static final class Accumulator {

		final Candidate chunk;
		Integer vectorRank;
		Integer keywordRank;
		double score;

		Accumulator(Candidate chunk) {
			this.chunk = chunk;
		}

	}

}
