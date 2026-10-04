package dev.akshita.speclens.mocktracker;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A stand-in for a project tracker, shaped like a small, read-only subset of the Jira REST
 * API (v3). It lives in the same app only to fit one free-tier service; SpecLens still calls
 * it over HTTP through a configurable base URL, exactly as it would call real Jira. See
 * docs/adr/0011.
 */
@RestController
@RequestMapping("/mock-tracker/rest/api/3")
@ConditionalOnProperty(name = "speclens.mock-tracker.enabled", havingValue = "true", matchIfMissing = true)
public class MockTrackerController {

	private final MockTrackerData data;
	private final boolean simulateOutage;

	public MockTrackerController(MockTrackerData data,
			@Value("${speclens.mock-tracker.simulate-outage:false}") boolean simulateOutage) {
		this.data = data;
		this.simulateOutage = simulateOutage;
	}

	/** Project facts the tools need, e.g. which sprint is "this sprint". */
	@GetMapping("/project")
	public ResponseEntity<?> project() {
		if (simulateOutage) {
			return outage();
		}
		var d = data.dataset();
		return ResponseEntity.ok(new ProjectView(d.project().key(), d.project().name(), d.snapshotDate(),
				d.currentSprint()));
	}

	@GetMapping("/issue/{key}")
	public ResponseEntity<?> issue(@PathVariable String key) {
		if (simulateOutage) {
			return outage();
		}
		return data.issue(key)
				.<ResponseEntity<?>>map(i -> ResponseEntity.ok(JiraIssue.of(i)))
				// Jira's own wording for a missing or hidden issue.
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
						"errorMessages", List.of("Issue does not exist or you do not have permission to see it."),
						"errors", Map.of())));
	}

	/**
	 * Real Jira takes a JQL query here, e.g. {@code labels = "BR-8.1" AND status = Blocked}.
	 * The mock accepts the same filters as plain parameters.
	 */
	@GetMapping("/search")
	public ResponseEntity<?> search(@RequestParam(required = false) String requirement,
			@RequestParam(required = false) String status, @RequestParam(required = false) String sprint) {
		if (simulateOutage) {
			return outage();
		}
		List<JiraIssue> issues = data.search(requirement, status, sprint).stream().map(JiraIssue::of).toList();
		return ResponseEntity.ok(new SearchResult(0, issues.size(), issues.size(), issues));
	}

	/** UAT executions; core Jira has none, so this stands in for a test-management plugin. */
	@GetMapping("/test-runs")
	public ResponseEntity<?> testRuns(@RequestParam(required = false) String requirement) {
		if (simulateOutage) {
			return outage();
		}
		List<TrackerDataset.TestRun> runs = data.testRuns(requirement);
		return ResponseEntity.ok(new TestRunResult(runs.size(), runs));
	}

	private static ResponseEntity<?> outage() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("errorMessages", List.of("Tracker is temporarily unavailable (simulated outage).")));
	}

	// ---- Jira-shaped response bodies ----

	public record ProjectView(String key, String name, String snapshotDate, String currentSprint) {
	}

	public record SearchResult(int startAt, int maxResults, int total, List<JiraIssue> issues) {
	}

	public record TestRunResult(int total, List<TrackerDataset.TestRun> testRuns) {
	}

	public record Named(String name) {
	}

	public record Assignee(String displayName) {
	}

	public record Fields(String summary, String description, Named status, Named issuetype, Named priority,
			Assignee assignee, List<String> labels, Named sprint, String updated) {
	}

	public record JiraIssue(String key, Fields fields) {

		static JiraIssue of(TrackerDataset.Issue i) {
			return new JiraIssue(i.key(), new Fields(i.summary(), i.description(), new Named(i.status()),
					new Named(i.type()), new Named(i.priority()), new Assignee(i.assignee()), i.requirements(),
					new Named(i.sprint()), i.updated()));
		}

	}

}
