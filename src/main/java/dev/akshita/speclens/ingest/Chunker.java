package dev.akshita.speclens.ingest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Splits page text into overlapping chunks. Chunks never cross a page boundary, so
 * every chunk has exactly one page number to cite. Within a page, a chunk is at most
 * {@code size} characters and ends at a sentence boundary when one is close enough;
 * consecutive chunks share about {@code overlap} characters so a requirement cut in
 * half still appears whole in one of them. See docs/adr/0004.
 */
@Component
public class Chunker {

	private final int size;
	private final int overlap;

	@Autowired
	public Chunker(IngestProperties properties) {
		this(properties.chunking().size(), properties.chunking().overlap());
	}

	public Chunker(int size, int overlap) {
		if (overlap < 0 || overlap >= size / 2) {
			throw new IllegalArgumentException("overlap must be >= 0 and < size/2");
		}
		this.size = size;
		this.overlap = overlap;
	}

	public List<ChunkDraft> chunk(List<PageText> pages) {
		List<ChunkDraft> chunks = new ArrayList<>();
		for (PageText page : pages) {
			for (String piece : splitPage(normalize(page.text()))) {
				// chunk_index is document-wide so chunks keep their reading order.
				chunks.add(new ChunkDraft(page.pageNumber(), chunks.size(), piece));
			}
		}
		return chunks;
	}

	List<String> splitPage(String text) {
		List<String> pieces = new ArrayList<>();
		int start = 0;
		int length = text.length();
		while (start < length) {
			int end = Math.min(start + size, length);
			if (end < length) {
				end = findBreak(text, start, end);
			}
			String piece = text.substring(start, end).strip();
			if (!piece.isEmpty()) {
				pieces.add(piece);
			}
			if (end >= length) {
				break;
			}
			start = nextStart(text, start, end);
		}
		return pieces;
	}

	/**
	 * Prefer ending after a sentence, then at a line break or space; only hard-cut if none
	 * is in the back half of the window.
	 */
	private int findBreak(String text, int start, int end) {
		int earliest = start + size / 2;
		for (int i = end - 1; i > earliest; i--) {
			char c = text.charAt(i - 1);
			if ((c == '.' || c == '?' || c == '!') && isBreak(text.charAt(i))) {
				return i;
			}
		}
		for (int i = end - 1; i >= earliest; i--) {
			if (isBreak(text.charAt(i))) {
				return i;
			}
		}
		return end;
	}

	/** Step back by the overlap, then forward to the next word start so no chunk begins mid-word. */
	private int nextStart(String text, int start, int end) {
		int next = end - overlap;
		for (int i = next; i < end; i++) {
			if (isBreak(text.charAt(i))) {
				next = i + 1;
				break;
			}
		}
		return next > start ? next : end;
	}

	private static boolean isBreak(char c) {
		return c == ' ' || c == '\n';
	}

	/**
	 * Collapses spaces and tabs inside each line and drops blank lines, but keeps line
	 * breaks, so table rows and list items stay on their own lines when a citation shows
	 * the passage. Sizes stay predictable: at most one whitespace character in a row.
	 */
	static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("\r", "")
				.replaceAll("[ \\t\\x0B\\f\\u00A0]+", " ")
				.replaceAll(" *\\n *", "\n")
				.replaceAll("\\n{2,}", "\n")
				.strip();
	}

}
