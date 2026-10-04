package dev.akshita.speclens.mocktracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the fictional tracker data once and answers lookups. Read-only by design: the
 * mock tracker has no endpoint that changes anything.
 */
@Component
public class MockTrackerData {

	private final TrackerDataset dataset;

	public MockTrackerData() {
		try (InputStream in = new ClassPathResource("mock-tracker/northwind-tracker.json").getInputStream()) {
			this.dataset = JsonMapper.builder().build().readValue(in, TrackerDataset.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not load the mock tracker data", ex);
		}
	}

	public TrackerDataset dataset() {
		return dataset;
	}

	public Optional<TrackerDataset.Issue> issue(String key) {
		return dataset.issues().stream().filter(i -> i.key().equalsIgnoreCase(key)).findFirst();
	}

	/** Every filter is optional; matching ignores case, like Jira's JQL equality. */
	public List<TrackerDataset.Issue> search(String requirement, String status, String sprint) {
		return dataset.issues().stream()
				.filter(i -> requirement == null || containsIgnoreCase(i.requirements(), requirement))
				.filter(i -> status == null || i.status().equalsIgnoreCase(status))
				.filter(i -> sprint == null || i.sprint().equalsIgnoreCase(sprint))
				.toList();
	}

	public List<TrackerDataset.TestRun> testRuns(String requirement) {
		return dataset.testRuns().stream()
				.filter(t -> requirement == null || containsIgnoreCase(t.requirements(), requirement))
				.toList();
	}

	private static boolean containsIgnoreCase(List<String> values, String wanted) {
		String w = wanted.toUpperCase(Locale.ROOT);
		return values.stream().anyMatch(v -> v.toUpperCase(Locale.ROOT).equals(w));
	}

}
