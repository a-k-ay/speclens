package dev.akshita.speclens.project;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL through JdbcClient (no JPA). Retrieval later needs pgvector and full-text
 * SQL anyway, so the whole app talks to Postgres the same explicit way.
 */
@Repository
public class ProjectRepository {

	private final JdbcClient jdbc;

	public ProjectRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Throws DuplicateKeyException if the name is taken (unique constraint uq_project_name). */
	public Project create(String name, String description) {
		return jdbc.sql("""
				INSERT INTO project (name, description)
				VALUES (:name, :description)
				RETURNING id, name, description, created_at
				""")
				.param("name", name)
				.param("description", description)
				.query(Project.class)
				.single();
	}

	public List<Project> findAll() {
		return jdbc.sql("SELECT id, name, description, created_at FROM project ORDER BY id")
				.query(Project.class)
				.list();
	}

	public Optional<Project> findById(long id) {
		return jdbc.sql("SELECT id, name, description, created_at FROM project WHERE id = :id")
				.param("id", id)
				.query(Project.class)
				.optional();
	}

}
