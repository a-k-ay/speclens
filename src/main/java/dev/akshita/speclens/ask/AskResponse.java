package dev.akshita.speclens.ask;

import java.util.List;

/**
 * @param answered false when SpecLens refused
 * @param refusalReason which safeguard refused; null when answered
 * @param citations the sources the answer cites; empty when refused
 */
public record AskResponse(String question, String answer, boolean answered, RefusalReason refusalReason,
		List<Citation> citations) {

	static AskResponse refused(String question, RefusalReason reason) {
		return new AskResponse(question, GroundedPrompt.REFUSAL, false, reason, List.of());
	}

	static AskResponse answered(String question, String answer, List<Citation> citations) {
		return new AskResponse(question, answer, true, null, citations);
	}

}
