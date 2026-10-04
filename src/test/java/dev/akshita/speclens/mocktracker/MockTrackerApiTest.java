package dev.akshita.speclens.mocktracker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.akshita.speclens.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The mock tracker's Jira-shaped endpoints, over HTTP (MockMvc). */
@IntegrationTest
class MockTrackerApiTest {

	private static final String API = "/mock-tracker/rest/api/3";

	@Autowired
	MockMvcTester mvc;

	@Test
	void issueIsReturnedInJiraShape() {
		assertThat(mvc.get().uri(API + "/issue/LOG-142"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.key", v -> v.assertThat().isEqualTo("LOG-142"))
				.hasPathSatisfying("$.fields.status.name", v -> v.assertThat().isEqualTo("Done"))
				.hasPathSatisfying("$.fields.labels", v -> v.assertThat().asArray().containsExactly("BR-8.1"))
				.hasPathSatisfying("$.fields.summary", v -> v.assertThat().asString().contains("24 hours"));
	}

	@Test
	void issueKeysAreCaseInsensitive() {
		assertThat(mvc.get().uri(API + "/issue/log-171")).hasStatusOk()
				.bodyJson().extractingPath("$.fields.status.name").isEqualTo("In Progress");
	}

	@Test
	void unknownIssueIs404WithJiraStyleError() {
		assertThat(mvc.get().uri(API + "/issue/LOG-999"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson().extractingPath("$.errorMessages[0]").asString().contains("does not exist");
	}

	@Test
	void searchByRequirementFindsEveryLinkedTicket() {
		assertThat(mvc.get().uri(API + "/search").param("requirement", "BR-8.1"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.total", v -> v.assertThat().isEqualTo(3))
				.hasPathSatisfying("$.issues[*].key",
						v -> v.assertThat().asArray().containsExactlyInAnyOrder("LOG-142", "LOG-171", "LOG-190"));
	}

	@Test
	void searchFiltersCombine() {
		assertThat(mvc.get().uri(API + "/search").param("status", "blocked"))
				.bodyJson().extractingPath("$.issues[*].key").asArray().containsExactlyInAnyOrder("LOG-106", "LOG-146");
		assertThat(mvc.get().uri(API + "/search").param("status", "In Progress").param("sprint", "Sprint 9"))
				.bodyJson().extractingPath("$.issues[*].key").asArray()
				.containsExactlyInAnyOrder("LOG-161", "LOG-171", "LOG-188", "LOG-189");
	}

	@Test
	void requirementWithoutTicketsReturnsAnEmptyList() {
		// BR-8.4 (payment terms) has no ticket: a real traceability gap the agent must report.
		assertThat(mvc.get().uri(API + "/search").param("requirement", "BR-8.4"))
				.bodyJson().extractingPath("$.total").isEqualTo(0);
	}

	@Test
	void testRunsForTheChangeRequestShowTheFailedUatCase() {
		assertThat(mvc.get().uri(API + "/test-runs").param("requirement", "CR-003"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.testRuns[0].testCase", v -> v.assertThat().isEqualTo("TC-I-01"))
				.hasPathSatisfying("$.testRuns[0].status", v -> v.assertThat().isEqualTo("FAIL"))
				.hasPathSatisfying("$.testRuns[0].defect", v -> v.assertThat().isEqualTo("LOG-190"));
	}

	@Test
	void projectReportsTheCurrentSprint() {
		assertThat(mvc.get().uri(API + "/project"))
				.bodyJson().extractingPath("$.currentSprint").isEqualTo("Sprint 9");
	}

}
