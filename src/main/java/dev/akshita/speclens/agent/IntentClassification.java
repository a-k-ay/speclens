package dev.akshita.speclens.agent;

import java.util.List;

/**
 * The classifier's structured output. Spring AI asks the model for JSON in exactly this
 * shape and maps it to this record.
 *
 * @param intent the question's intent
 * @param confidence 0 to 1, the model's own confidence
 * @param requirementIds requirement or change-request IDs named in the question, e.g. BR-8.1, CR-003
 * @param ticketIds tracker ticket keys named in the question, e.g. LOG-142
 * @param reason one short sentence explaining the choice (for logs and the eval report)
 */
public record IntentClassification(Intent intent, double confidence, List<String> requirementIds,
		List<String> ticketIds, String reason) {

	public IntentClassification {
		requirementIds = requirementIds == null ? List.of() : List.copyOf(requirementIds);
		ticketIds = ticketIds == null ? List.of() : List.copyOf(ticketIds);
	}

}
