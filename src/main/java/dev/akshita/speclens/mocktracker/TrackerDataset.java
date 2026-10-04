package dev.akshita.speclens.mocktracker;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fictional tracker data in mock-tracker/northwind-tracker.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrackerDataset(Project project, String snapshotDate, String currentSprint, List<Issue> issues,
		List<TestRun> testRuns) {

	public record Project(String key, String name) {
	}

	/** A ticket. {@code requirements} plays the role of Jira labels such as "BR-8.1". */
	public record Issue(String key, String type, String status, String sprint, String priority, String assignee,
			List<String> requirements, String updated, String summary, String description) {
	}

	/** A UAT test execution, as a test-management plugin (e.g. Xray) would record it. */
	public record TestRun(String testCase, String title, List<String> requirements, String status,
			String executedOn, String executedBy, String defect, String comment) {
	}

}
