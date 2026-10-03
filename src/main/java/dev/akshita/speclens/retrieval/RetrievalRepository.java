package dev.akshita.speclens.retrieval;

import java.util.List;

import dev.akshita.speclens.ai.Vectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The two searches behind hybrid retrieval. Both filter by project_id first, so a
 * question can never see another project's documents.
 */
@Repository
public class RetrievalRepository {

	private final JdbcClient jdbc;

	public RetrievalRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Nearest chunks by cosine distance (<=>), served by the HNSW index. */
	public List<Candidate> vectorSearch(long projectId, float[] questionVector, int limit) {
		return jdbc.sql("""
				SELECT c.id AS chunk_id, c.document_id, d.filename AS document_name, c.page_number, c.content,
				       1 - (c.embedding <=> CAST(:vector AS vector)) AS similarity
				FROM chunk c
				JOIN document d ON d.id = c.document_id
				WHERE c.project_id = :projectId
				ORDER BY c.embedding <=> CAST(:vector AS vector)
				LIMIT :limit
				""")
				.param("projectId", projectId)
				.param("vector", Vectors.toPgVector(questionVector))
				.param("limit", limit)
				.query(Candidate.class)
				.list();
	}

	/**
	 * Full-text search, ranked by ts_rank_cd. plainto_tsquery stems the question and drops
	 * stop words, but joins terms with AND, so a natural question ("how soon must we
	 * invoice the customer") would only match a chunk containing every word. Swapping
	 * '&' for '|' turns it into OR, and ts_rank_cd still ranks chunks that match more
	 * terms, closer together, higher. The rewritten query is parsed with the 'simple'
	 * config so the already-stemmed terms aren't stemmed twice.
	 */
	public List<Candidate> keywordSearch(long projectId, String question, float[] questionVector, int limit) {
		return jdbc.sql("""
				WITH q AS (
				    SELECT to_tsquery('simple', replace(plainto_tsquery('english', :question)::text, ' & ', ' | ')) AS tsq
				)
				SELECT c.id AS chunk_id, c.document_id, d.filename AS document_name, c.page_number, c.content,
				       1 - (c.embedding <=> CAST(:vector AS vector)) AS similarity
				FROM chunk c
				JOIN document d ON d.id = c.document_id
				CROSS JOIN q
				WHERE c.project_id = :projectId
				  AND c.content_tsv @@ q.tsq
				ORDER BY ts_rank_cd(c.content_tsv, q.tsq) DESC, c.id
				LIMIT :limit
				""")
				.param("projectId", projectId)
				.param("question", question)
				.param("vector", Vectors.toPgVector(questionVector))
				.param("limit", limit)
				.query(Candidate.class)
				.list();
	}

}
