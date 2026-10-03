package dev.akshita.speclens.retrieval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * speclens.retrieval.* settings.
 *
 * @param candidates how many chunks each search returns before fusion
 * @param topK how many fused chunks are given to the model as sources
 * @param rrfK the k constant in 1 / (k + rank)
 * @param minSimilarity refuse without calling the model when the best source's cosine
 * similarity is below this (see docs/adr/0006)
 */
@Validated
@ConfigurationProperties("speclens.retrieval")
public record RetrievalProperties(@Min(1) int candidates, @Min(1) int topK, @Min(1) int rrfK,
		@DecimalMin("0.0") @DecimalMax("1.0") double minSimilarity) {
}
