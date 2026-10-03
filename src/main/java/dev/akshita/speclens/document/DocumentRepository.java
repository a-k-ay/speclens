package dev.akshita.speclens.document;

import java.util.List;
import java.util.Map;

import dev.akshita.speclens.ai.Vectors;
import dev.akshita.speclens.ingest.ChunkDraft;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

	private final JdbcClient jdbc;
	private final JdbcTemplate jdbcTemplate;

	public DocumentRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
		this.jdbc = jdbc;
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean exists(long projectId, String filename) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM document WHERE project_id = :projectId AND filename = :filename)")
				.param("projectId", projectId)
				.param("filename", filename)
				.query(Boolean.class)
				.single();
	}

	public long insertDocument(long projectId, String filename, String contentType, int pageCount) {
		return jdbc.sql("""
				INSERT INTO document (project_id, filename, content_type, page_count)
				VALUES (:projectId, :filename, :contentType, :pageCount)
				RETURNING id
				""")
				.param("projectId", projectId)
				.param("filename", filename)
				.param("contentType", contentType)
				.param("pageCount", pageCount)
				.query(Long.class)
				.single();
	}

	/** One batched round trip for all chunks. content_tsv is filled in by Postgres. */
	public void insertChunks(long documentId, long projectId, List<ChunkDraft> chunks, List<float[]> embeddings) {
		jdbcTemplate.batchUpdate("""
				INSERT INTO chunk (document_id, project_id, page_number, chunk_index, content, embedding)
				VALUES (?, ?, ?, ?, ?, ?::vector)
				""", chunks, 200, (ps, chunk) -> {
			ps.setLong(1, documentId);
			ps.setLong(2, projectId);
			ps.setInt(3, chunk.pageNumber());
			ps.setInt(4, chunk.chunkIndex());
			ps.setString(5, chunk.content());
			ps.setString(6, Vectors.toPgVector(embeddings.get(chunk.chunkIndex())));
		});
	}

	public void insertGlossary(long documentId, Map<String, String> glossary) {
		jdbcTemplate.batchUpdate("INSERT INTO glossary_term (document_id, short_form, long_form) VALUES (?, ?, ?)",
				List.copyOf(glossary.entrySet()), 100, (ps, term) -> {
					ps.setLong(1, documentId);
					ps.setString(2, term.getKey());
					ps.setString(3, term.getValue());
				});
	}

	public List<GlossaryTerm> findGlossary(long projectId) {
		return jdbc.sql("""
				SELECT g.short_form, g.long_form, d.filename AS document_name
				FROM glossary_term g
				JOIN document d ON d.id = g.document_id
				WHERE d.project_id = :projectId
				ORDER BY d.id, g.short_form
				""")
				.param("projectId", projectId)
				.query(GlossaryTerm.class)
				.list();
	}

	public List<DocumentSummary> findByProject(long projectId) {
		return jdbc.sql(SELECT_SUMMARY + " WHERE d.project_id = :projectId ORDER BY d.id")
				.param("projectId", projectId)
				.query(DocumentSummary.class)
				.list();
	}

	public DocumentSummary findSummary(long documentId) {
		return jdbc.sql(SELECT_SUMMARY + " WHERE d.id = :id")
				.param("id", documentId)
				.query(DocumentSummary.class)
				.single();
	}

	private static final String SELECT_SUMMARY = """
			SELECT d.id, d.project_id, d.filename, d.content_type, d.page_count,
			       (SELECT count(*) FROM chunk c WHERE c.document_id = d.id) AS chunk_count,
			       d.uploaded_at
			FROM document d
			""";

}
