package dev.akshita.speclens;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context + real Postgres/pgvector container + fake AI models.
 * All integration tests share one annotation, so Spring caches and reuses one context
 * (and one container) across test classes instead of starting a new one per class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, IntegrationTest.FakeAiConfiguration.class })
public @interface IntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	class FakeAiConfiguration {

		@Bean
		EmbeddingModel embeddingModel() {
			return new FakeEmbeddingModel();
		}

		@Bean
		FakeChatModel chatModel() {
			return new FakeChatModel();
		}

	}

}
