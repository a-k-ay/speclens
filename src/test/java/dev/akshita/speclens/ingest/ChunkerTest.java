package dev.akshita.speclens.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ChunkerTest {

	private final Chunker chunker = new Chunker(1000, 150);

	@Test
	void shortPageBecomesOneChunk() {
		List<ChunkDraft> chunks = chunker.chunk(List.of(new PageText(1, "The system shall export invoices.")));

		assertThat(chunks).containsExactly(new ChunkDraft(1, 0, "The system shall export invoices."));
	}

	@Test
	void chunksNeverSpanPagesAndIndexIsDocumentWide() {
		List<ChunkDraft> chunks = chunker.chunk(List.of(
				new PageText(1, "Page one text."),
				new PageText(2, "Page two text.")));

		assertThat(chunks).extracting(ChunkDraft::pageNumber).containsExactly(1, 2);
		assertThat(chunks).extracting(ChunkDraft::chunkIndex).containsExactly(0, 1);
		assertThat(chunks.get(1).content()).isEqualTo("Page two text.");
	}

	@Test
	void blankPagesProduceNoChunks() {
		List<ChunkDraft> chunks = chunker.chunk(List.of(new PageText(1, "  \n\t "), new PageText(2, "Text.")));

		assertThat(chunks).singleElement().extracting(ChunkDraft::pageNumber).isEqualTo(2);
	}

	@Test
	void longPageIsSplitIntoBoundedOverlappingChunksEndingAtSentences() {
		String page = sentences(60); // ~3,000 characters
		List<ChunkDraft> chunks = chunker.chunk(List.of(new PageText(3, page)));

		assertThat(chunks).hasSizeGreaterThan(2);
		assertThat(chunks).allSatisfy(c -> {
			assertThat(c.content().length()).isLessThanOrEqualTo(1000);
			assertThat(c.pageNumber()).isEqualTo(3);
		});
		// Every chunk except the last ends at a sentence boundary.
		chunks.subList(0, chunks.size() - 1).forEach(c -> assertThat(c.content()).endsWith("."));
		// Consecutive chunks overlap: the next chunk starts with text from the end of the previous one.
		for (int i = 1; i < chunks.size(); i++) {
			String previous = chunks.get(i - 1).content();
			String firstWords = chunks.get(i).content().substring(0, 20);
			assertThat(previous).contains(firstWords);
		}
		// Nothing is lost: every sentence appears in some chunk.
		IntStream.range(0, 60).forEach(n -> assertThat(chunks).anySatisfy(
				c -> assertThat(c.content()).contains("Requirement " + n + " ")));
	}

	@Test
	void textWithoutSentenceBreaksStillSplitsAtSpacesNotMidWord() {
		String page = IntStream.range(0, 400).mapToObj(i -> "word" + i).collect(Collectors.joining(" "));
		List<ChunkDraft> chunks = chunker.chunk(List.of(new PageText(1, page)));

		assertThat(chunks).hasSizeGreaterThan(1);
		assertThat(chunks).allSatisfy(c -> {
			assertThat(c.content()).startsWith("word");
			assertThat(c.content()).matches(".*word\\d+$");
		});
	}

	@Test
	void whitespaceIsNormalisedButLineBreaksAreKept() {
		assertThat(Chunker.normalize("  Line one\r\n\r\n   wrapped\t\tline  \n")).isEqualTo("Line one\nwrapped line");
	}

	@Test
	void tableRowsStayOnTheirOwnLines() {
		String table = """
				S.NO  DESCRIPTION   UNIT  QUANTITY  RATE
				1     DB            Mtrs  12        300
				2     APFC Panel    Pocket 5        450
				""";

		List<ChunkDraft> chunks = chunker.chunk(List.of(new PageText(1, table)));

		assertThat(chunks.getFirst().content()).isEqualTo("""
				S.NO DESCRIPTION UNIT QUANTITY RATE
				1 DB Mtrs 12 300
				2 APFC Panel Pocket 5 450""");
	}

	@Test
	void longTextWithLineBreaksSplitsAtLineBreaksNotMidWord() {
		String page = IntStream.range(0, 120).mapToObj(i -> "row " + i + " value " + (i * 7))
				.collect(Collectors.joining("\n"));

		List<ChunkDraft> chunks = chunker.chunk(List.of(new PageText(1, page)));

		// Every chunk starts and ends with a whole token ("row", "value" or a number), never "ow" or "alu".
		assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(c ->
				assertThat(c.content()).matches("(?s)^(row|value|\\d+)\\s.*\\s(row|value|\\d+)$"));
	}

	@Test
	void rejectsOverlapThatWouldStallProgress() {
		assertThatThrownBy(() -> new Chunker(1000, 600)).isInstanceOf(IllegalArgumentException.class);
	}

	private static String sentences(int count) {
		return IntStream.range(0, count)
				.mapToObj(n -> "Requirement " + n + " says the warehouse module shall record each pallet scan.")
				.collect(Collectors.joining(" "));
	}

}
