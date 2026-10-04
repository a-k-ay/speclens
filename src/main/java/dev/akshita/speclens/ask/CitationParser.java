package dev.akshita.speclens.ask;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads source markers out of the model's answer. Models write them as [S1] or grouped,
 * [S1, S2] or [S3, TC-I-01], so every S-number inside any square brackets counts.
 */
final class CitationParser {

	private static final Pattern BRACKETS = Pattern.compile("\\[([^\\[\\]]{1,80})]");
	private static final Pattern SOURCE = Pattern.compile("\\bS(\\d+)\\b");

	private CitationParser() {
	}

	/**
	 * Zero-based source indexes in order of first mention. Markers pointing at sources
	 * that don't exist (e.g. [S9] when there were 5) are dropped, so a hallucinated
	 * citation can never reach the user.
	 */
	static List<Integer> citedSourceIndexes(String answer, int sourceCount) {
		Set<Integer> indexes = new LinkedHashSet<>();
		Matcher brackets = BRACKETS.matcher(answer);
		while (brackets.find()) {
			Matcher m = SOURCE.matcher(brackets.group(1));
			while (m.find()) {
				int index = Integer.parseInt(m.group(1)) - 1;
				if (index >= 0 && index < sourceCount) {
					indexes.add(index);
				}
			}
		}
		return List.copyOf(indexes);
	}

	static boolean isRefusal(String answer) {
		return answer.strip().startsWith(GroundedPrompt.REFUSAL.substring(0, GroundedPrompt.REFUSAL.length() - 1));
	}

}
