package dev.akshita.speclens.ingest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Binds the speclens.* settings in application.yml; the app refuses to start if they are invalid. */
@Validated
@ConfigurationProperties("speclens")
public record IngestProperties(@Valid Upload upload, @Valid Chunking chunking) {

	public record Upload(boolean enabled, @Min(1) int maxPages) {
	}

	public record Chunking(@Min(200) int size, @Min(0) int overlap) {

		// Overlap must be well below size or the chunker would barely move forward.
		@AssertTrue(message = "speclens.chunking.overlap must be less than half of size")
		public boolean isOverlapValid() {
			return overlap < size / 2;
		}

	}

}
