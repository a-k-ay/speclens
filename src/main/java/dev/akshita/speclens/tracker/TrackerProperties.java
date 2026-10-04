package dev.akshita.speclens.tracker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * speclens.tracker.* settings for calling the project tracker.
 *
 * @param baseUrl the tracker's REST base URL; blank means the mock tracker inside this app
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for a response
 */
@ConfigurationProperties("speclens.tracker")
public record TrackerProperties(String baseUrl, @DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("3s") Duration readTimeout) {
}
