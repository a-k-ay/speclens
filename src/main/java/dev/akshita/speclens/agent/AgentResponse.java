package dev.akshita.speclens.agent;

import java.util.List;

import dev.akshita.speclens.ask.AskResponse;
import dev.akshita.speclens.ask.Citation;
import dev.akshita.speclens.ask.GroundedPrompt;
import dev.akshita.speclens.ask.RefusalReason;
import dev.akshita.speclens.tracker.ToolCallRecord;

/**
 * The answer to POST /api/projects/{id}/ask. The first five fields are the same as the
 * document-only answer; the rest show how the question was routed and what was called.
 *
 * @param route which systems were used
 * @param intent the classified intent; {@code routedByFallback} is true when the classifier
 * wasn't confident (or failed) and the document path was used instead
 * @param toolCalls every tracker call made, in order
 * @param trackerRefs ticket keys and test ids the answer cites
 * @param notice a message to show with the answer, e.g. that live data was unavailable
 */
public record AgentResponse(String question, String answer, boolean answered, RefusalReason refusalReason,
		List<Citation> citations, Route route, Intent intent, double intentConfidence, boolean routedByFallback,
		List<ToolCallRecord> toolCalls, List<String> trackerRefs, String notice) {

	public enum Route {

		DOCUMENTS, TRACKER, DOCUMENTS_AND_TRACKER, NONE

	}

	static AgentResponse fromDocuments(AskResponse doc, IntentClassification c, boolean fallback,
			List<ToolCallRecord> toolCalls, String notice) {
		return new AgentResponse(doc.question(), doc.answer(), doc.answered(), doc.refusalReason(), doc.citations(),
				Route.DOCUMENTS, c.intent(), c.confidence(), fallback, toolCalls, List.of(), notice);
	}

	static AgentResponse refused(String question, String answer, RefusalReason reason, Route route,
			IntentClassification c, List<ToolCallRecord> toolCalls, String notice) {
		return new AgentResponse(question, answer == null ? GroundedPrompt.REFUSAL : answer, false, reason, List.of(),
				route, c.intent(), c.confidence(), false, toolCalls, List.of(), notice);
	}

}
