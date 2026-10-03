package dev.akshita.speclens.ask;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads [S1]-style markers out of the model's answer. */
final class CitationParser {

	private static final Pattern MARKER = Pattern.compile("\\[S(\\d+)]");

	private CitationParser() {
	}

	/**
	 * Zero-based source indexes in order of first mention. Markers pointing at sources
	 * that don't exist (e.g. [S9] when there were 5) are dropped, so a hallucinated
	 * citation can never reach the user.
	 */
	static List<Integer> citedSourceIndexes(String answer, int sourceCount) {
		Set<Integer> indexes = new LinkedHashSet<>();
		Matcher m = MARKER.matcher(answer);
		while (m.find()) {
			int index = Integer.parseInt(m.group(1)) - 1;
			if (index >= 0 && index < sourceCount) {
				indexes.add(index);
			}
		}
		return List.copyOf(indexes);
	}

	static boolean isRefusal(String answer) {
		return answer.strip().startsWith(GroundedPrompt.REFUSAL.substring(0, GroundedPrompt.REFUSAL.length() - 1));
	}

}
