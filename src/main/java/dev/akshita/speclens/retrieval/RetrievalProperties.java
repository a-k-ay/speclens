package dev.akshita.speclens.retrieval;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * speclens.retrieval.* settings.
 *
 * @param candidates how many chunks each search returns before fusion
 * @param topK how many fused chunks are given to the model as sources
 * @param rrfK the k constant in 1 / (k + rank)
 */
@Validated
@ConfigurationProperties("speclens.retrieval")
public record RetrievalProperties(@Min(1) int candidates, @Min(1) int topK, @Min(1) int rrfK) {
}
