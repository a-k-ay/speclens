package dev.akshita.speclens.ask;

import java.util.List;

/**
 * @param answered false when SpecLens refused (nothing relevant found)
 * @param citations the sources the answer cites; empty when refused
 */
public record AskResponse(String question, String answer, boolean answered, List<Citation> citations) {

	static AskResponse refused(String question) {
		return new AskResponse(question, GroundedPrompt.REFUSAL, false, List.of());
	}

}
