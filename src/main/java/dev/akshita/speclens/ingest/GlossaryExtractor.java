package dev.akshita.speclens.ingest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Finds abbreviation definitions such as "electronic proof of delivery (e-POD)" in a
 * document, so the definition can travel with chunks that only say "e-POD".
 *
 * Uses the Schwartz-Hearst algorithm (Schwartz and Hearst, 2003): for a short form in
 * parentheses, walk backwards through the preceding words and match the short form's
 * letters, right to left, against the long form; the first letter must start a word.
 */
@Component
public class GlossaryExtractor {

	/** "(e-POD)", "(UAT)", "(INR)": 2 to 10 characters, starting with a letter. */
	private static final Pattern SHORT_FORM = Pattern.compile("\\(([A-Za-z][A-Za-z0-9-]{1,9})\\)");

	public Map<String, String> extract(List<PageText> pages) {
		Map<String, String> glossary = new LinkedHashMap<>();
		for (PageText page : pages) {
			String text = Chunker.normalize(page.text());
			Matcher m = SHORT_FORM.matcher(text);
			while (m.find()) {
				String shortForm = m.group(1);
				if (!isPlausibleShortForm(shortForm) || glossary.containsKey(shortForm)) {
					continue;
				}
				String longForm = findLongForm(shortForm, text.substring(0, m.start()).strip());
				if (longForm != null) {
					glossary.put(shortForm, longForm);
				}
			}
		}
		return glossary;
	}

	/** Abbreviations have at least two capitals (UAT, e-POD, PO); "(Northwind)" is just a word. */
	private static boolean isPlausibleShortForm(String s) {
		return s.chars().filter(Character::isUpperCase).count() >= 2;
	}

	/**
	 * Looks at most min(letters + 5, letters * 2) words back, as in the original paper, and
	 * returns the shortest preceding phrase whose words contain the short form's letters in
	 * order, with the first letter starting a word.
	 */
	static String findLongForm(String shortForm, String before) {
		String letters = shortForm.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		String[] words = before.split(" ");
		int maxWords = Math.min(letters.length() + 5, letters.length() * 2);
		for (int n = 1; n <= Math.min(maxWords, words.length); n++) {
			String candidate = String.join(" ",
					java.util.Arrays.copyOfRange(words, words.length - n, words.length));
			if (matches(letters, candidate.toLowerCase())) {
				// Long forms are phrases, not punctuation; strip a trailing comma or quote.
				return candidate.replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", "");
			}
		}
		return null;
	}

	/** Schwartz-Hearst core: match letters right to left; the first must begin a word. */
	private static boolean matches(String letters, String longForm) {
		int s = letters.length() - 1;
		int l = longForm.length() - 1;
		while (s >= 0) {
			char c = letters.charAt(s);
			while (l >= 0 && (longForm.charAt(l) != c
					|| (s == 0 && l > 0 && Character.isLetterOrDigit(longForm.charAt(l - 1))))) {
				l--;
			}
			if (l < 0) {
				return false;
			}
			l--;
			s--;
		}
		return true;
	}

}
