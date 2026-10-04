package dev.akshita.speclens.tracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;

import dev.akshita.speclens.tracker.TrackerModels.JiraIssue;
import dev.akshita.speclens.tracker.TrackerModels.ProjectInfo;
import dev.akshita.speclens.tracker.TrackerModels.SearchResponse;
import dev.akshita.speclens.tracker.TrackerModels.TestRun;
import dev.akshita.speclens.tracker.TrackerModels.TestRunResponse;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;

import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the project tracker over HTTP. It knows only the tracker's REST contract (a subset
 * of Jira's), so pointing speclens.tracker.base-url at another tracker needs no code change.
 * Every call has a connect and a read timeout; any network failure, timeout or 5xx becomes a
 * TrackerUnavailableException so callers can fall back to documents only.
 */
@Component
public class TrackerClient {

	private final TrackerProperties properties;
	private final Environment environment;
	private final RestClient http;

	public TrackerClient(TrackerProperties properties, Environment environment) {
		this.properties = properties;
		this.environment = environment;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.http = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** Empty when the ticket doesn't exist (HTTP 404). */
	public Optional<Ticket> ticket(String key) {
		try {
			return Optional.ofNullable(get(uri("/issue/{key}", key), JiraIssue.class)).map(JiraIssue::toTicket);
		}
		catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
	}

	/** Every filter is optional (null = not filtered). */
	public List<Ticket> search(String requirement, String status, String sprint) {
		UriComponentsBuilder b = UriComponentsBuilder.fromUriString(baseUrl() + "/search");
		if (requirement != null) {
			b.queryParam("requirement", requirement);
		}
		if (status != null) {
			b.queryParam("status", status);
		}
		if (sprint != null) {
			b.queryParam("sprint", sprint);
		}
		SearchResponse response = get(b.encode().build().toUri(), SearchResponse.class);
		return response == null || response.issues() == null ? List.of()
				: response.issues().stream().map(JiraIssue::toTicket).toList();
	}

	public List<TestRun> testRuns(String requirement) {
		URI uri = UriComponentsBuilder.fromUriString(baseUrl() + "/test-runs")
				.queryParam("requirement", requirement).encode().build().toUri();
		TestRunResponse response = get(uri, TestRunResponse.class);
		return response == null || response.testRuns() == null ? List.of() : response.testRuns();
	}

	public ProjectInfo project() {
		return get(uri("/project"), ProjectInfo.class);
	}

	private <T> T get(URI uri, Class<T> type) {
		try {
			return http.get().uri(uri).retrieve().body(type);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw ex; // a normal "no such ticket" answer, handled by the caller
		}
		catch (RestClientException ex) {
			// Connection refused, timeout, 5xx (e.g. 503 during an outage), unreadable body.
			throw new TrackerUnavailableException("Tracker call failed: " + ex.getMessage(), ex);
		}
	}

	private URI uri(String path, Object... variables) {
		return UriComponentsBuilder.fromUriString(baseUrl() + path).buildAndExpand(variables).encode().toUri();
	}

	/** The configured URL, or this app's own mock tracker on whatever port it is running. */
	String baseUrl() {
		if (StringUtils.hasText(properties.baseUrl())) {
			return properties.baseUrl().replaceAll("/+$", "");
		}
		String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
		return "http://localhost:" + port + "/mock-tracker/rest/api/3";
	}

}
