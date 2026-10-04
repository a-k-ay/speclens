package dev.akshita.speclens.mocktracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

/** Guards the hand-written seed data against typos, and the outage switch. */
class MockTrackerDataTest {

	private final MockTrackerData data = new MockTrackerData();

	@Test
	void everyIdFollowsItsFormat() {
		assertThat(data.dataset().issues()).allSatisfy(i -> {
			assertThat(i.key()).matches("LOG-\\d+");
			assertThat(i.requirements()).isNotEmpty().allMatch(r -> r.matches("(BR|NFR)-\\d+(\\.\\d+)?|CR-\\d{3}"));
			assertThat(i.status()).isIn("To Do", "In Progress", "In Review", "Blocked", "Done");
		});
		assertThat(data.dataset().testRuns()).allSatisfy(t -> {
			assertThat(t.testCase()).matches("TC-[A-Z]-\\d{2}");
			assertThat(t.status()).isIn("PASS", "FAIL", "NOT_RUN");
		});
	}

	@Test
	void everyDefectReferencedByATestRunExistsAndFailedRunsHaveOne() {
		data.dataset().testRuns().stream().map(TrackerDataset.TestRun::defect).filter(Objects::nonNull)
				.forEach(defect -> assertThat(data.issue(defect)).as("defect %s", defect).isPresent());
		assertThat(data.dataset().testRuns()).filteredOn(t -> t.status().equals("FAIL"))
				.allSatisfy(t -> assertThat(t.defect()).isNotNull());
	}

	@Test
	void issueKeysAreUnique() {
		assertThat(data.dataset().issues()).extracting(TrackerDataset.Issue::key).doesNotHaveDuplicates();
	}

	@Test
	void simulatedOutageAnswers503() {
		MockTrackerController down = new MockTrackerController(data, true);

		assertThat(down.issue("LOG-142").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(down.search("BR-8.1", null, null).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

}
