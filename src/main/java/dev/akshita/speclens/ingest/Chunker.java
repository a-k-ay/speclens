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

	/** Prefer ending after a sentence, then at a space; only hard-cut if neither is in the back half. */
	private int findBreak(String text, int start, int end) {
		int earliest = start + size / 2;
		String window = text.substring(earliest, end);
		int sentenceEnd = Math.max(window.lastIndexOf(". "),
				Math.max(window.lastIndexOf("? "), window.lastIndexOf("! ")));
		if (sentenceEnd >= 0) {
			return earliest + sentenceEnd + 1;
		}
		int space = window.lastIndexOf(' ');
		return space >= 0 ? earliest + space : end;
	}

	/** Step back by the overlap, then forward to the next word start so no chunk begins mid-word. */
	private int nextStart(String text, int start, int end) {
		int next = end - overlap;
		int space = text.indexOf(' ', next);
		if (space >= 0 && space < end) {
			next = space + 1;
		}
		return next > start ? next : end;
	}

	/** PDF text has hard line wraps and repeated spaces; one space keeps sizes predictable. */
	static String normalize(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip();
	}

}
