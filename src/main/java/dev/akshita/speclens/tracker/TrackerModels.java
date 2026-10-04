package dev.akshita.speclens.tracker;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What SpecLens knows about the tracker: the JSON it receives over HTTP (Jira's shape) and
 * the plain records the tools hand to the model. Deliberately independent of the mock
 * tracker's own classes, so the client depends only on the HTTP contract.
 */
public final class TrackerModels {

	private TrackerModels() {
	}

	/** A ticket, flattened from Jira's nested fields. */
	public record Ticket(String key, String type, String summary, String status, String sprint, String assignee,
			String priority, List<String> requirements, String updated, String description) {
	}

	/** One UAT test execution. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TestRun(String testCase, String title, List<String> requirements, String status, String executedOn,
			String executedBy, String defect, String comment) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ProjectInfo(String key, String name, String snapshotDate, String currentSprint) {
	}

	// ---- Jira-shaped JSON as received ----

	@JsonIgnoreProperties(ignoreUnknown = true)
	record JiraIssue(String key, JiraFields fields) {

		Ticket toTicket() {
			JiraFields f = fields;
			return new Ticket(key, name(f.issuetype()), f.summary(), name(f.status()), name(f.sprint()),
					f.assignee() == null ? null : f.assignee().displayName(), name(f.priority()),
					f.labels() == null ? List.of() : f.labels(), f.updated(), f.description());
		}

		private static String name(Named n) {
			return n == null ? null : n.name();
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record JiraFields(String summary, String description, Named status, Named issuetype, Named priority,
			Assignee assignee, List<String> labels, Named sprint, String updated) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Named(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Assignee(String displayName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchResponse(int total, List<JiraIssue> issues) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TestRunResponse(int total, List<TestRun> testRuns) {
	}

}
