package dev.akshita.speclens.web;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * speclens.rate-limit.* settings. Protects the Gemini key on the public demo.
 *
 * @param perIpPerMinute requests one client may make per minute
 * @param perIpPerDay requests one client may make per UTC day
 * @param globalPerDay requests all clients together may make per UTC day
 */
@Validated
@ConfigurationProperties("speclens.rate-limit")
public record RateLimitProperties(@Min(1) int perIpPerMinute, @Min(1) int perIpPerDay, @Min(1) int globalPerDay) {
}
