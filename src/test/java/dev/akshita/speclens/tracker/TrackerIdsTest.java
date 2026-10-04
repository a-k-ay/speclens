package dev.akshita.speclens.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.akshita.speclens.tracker.TrackerIds.InvalidToolArgumentException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TrackerIdsTest {

	@ParameterizedTest
	@CsvSource({ "LOG-142, LOG-142", "log-142, LOG-142", "' LOG-7 ', LOG-7" })
	void ticketKeysAreNormalised(String raw, String expected) {
		assertThat(TrackerIds.ticket(raw)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "LOG-1; DROP TABLE", "../admin", "LOG-", "JIRA-142", "LOG-1234567", "all tickets", "" })
	void anythingThatIsNotATicketKeyIsRejected(String raw) {
		assertThatThrownBy(() -> TrackerIds.ticket(raw)).isInstanceOf(InvalidToolArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({ "BR-8.1, BR-8.1", "br-8.1, BR-8.1", "BR 7.4, BR-7.4", "NFR-4, NFR-4", "CR-3, CR-003",
			"cr-003, CR-003", "CR3, CR-003", "'CR 3', CR-003" })
	void requirementIdsAreNormalised(String raw, String expected) {
		assertThat(TrackerIds.requirement(raw)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "BR-8.1.5.6", "XX-1", "BR-", "8.1", "CR-1234", "BR-8.1 OR 1=1" })
	void malformedRequirementIdsAreRejected(String raw) {
		assertThatThrownBy(() -> TrackerIds.requirement(raw)).isInstanceOf(InvalidToolArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({ "blocked, Blocked", "IN PROGRESS, In Progress", "to do, To Do" })
	void statusesMatchCaseInsensitively(String raw, String expected) {
		assertThat(TrackerIds.status(raw)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "stuck", "Closed", "" })
	void unknownStatusesAreRejected(String raw) {
		assertThatThrownBy(() -> TrackerIds.status(raw)).isInstanceOf(InvalidToolArgumentException.class)
				.hasMessageContaining("Blocked");
	}

	@ParameterizedTest
	@CsvSource({ "Sprint 9, Sprint 9", "sprint 9, Sprint 9", "9, Sprint 9", "current, CURRENT", "this sprint, CURRENT" })
	void sprintsAreNormalised(String raw, String expected) {
		assertThat(TrackerIds.sprint(raw)).isEqualTo(expected);
	}

}
