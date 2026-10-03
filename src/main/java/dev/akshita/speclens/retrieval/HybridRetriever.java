package dev.akshita.speclens.retrieval;

import java.util.List;

import org.springframework.stereotype.Service;

/** Vector search + keyword search, merged with Reciprocal Rank Fusion. */
@Service
public class HybridRetriever {

	private final RetrievalRepository repository;
	private final RetrievalProperties properties;

	public HybridRetriever(RetrievalRepository repository, RetrievalProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	/** {@code questionVector} is computed by the caller so the question is embedded only once. */
	public List<RetrievedChunk> retrieve(long projectId, String question, float[] questionVector) {
		List<Candidate> byMeaning = repository.vectorSearch(projectId, questionVector, properties.candidates());
		List<Candidate> byKeyword = repository.keywordSearch(projectId, question, questionVector,
				properties.candidates());
		return ReciprocalRankFusion.fuse(byMeaning, byKeyword, properties.rrfK(), properties.topK());
	}

}
