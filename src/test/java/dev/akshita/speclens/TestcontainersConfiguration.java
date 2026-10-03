package dev.akshita.speclens;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Same image as docker-compose.yml and Neon (Postgres 17 + pgvector), so tests
	// run against the real vector extension, not an in-memory stand-in.
	public static final DockerImageName PGVECTOR_IMAGE =
			DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(PGVECTOR_IMAGE);
	}

}
