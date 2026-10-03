package dev.akshita.speclens.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.akshita.speclens.TestDocuments;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

	private final TextExtractor extractor = new TextExtractor();

	@Test
	void pdfTextIsExtractedPerPage() {
		byte[] pdf = TestDocuments.pdf("Scope covers freight booking.", "Out of scope: customs clearance.");

		List<PageText> pages = extractor.extract(extractor.detect("sow.pdf", pdf), pdf);

		assertThat(pages).extracting(PageText::pageNumber).containsExactly(1, 2);
		assertThat(pages.get(0).text()).contains("Scope covers freight booking.");
		assertThat(pages.get(1).text()).contains("Out of scope: customs clearance.");
	}

	@Test
	void docxIsSplitOnPageBreaks() {
		byte[] docx = TestDocuments.docx("Meeting notes, page one.", "Action items, page two.");

		List<PageText> pages = extractor.extract(extractor.detect("notes.docx", docx), docx);

		assertThat(pages).extracting(PageText::pageNumber).containsExactly(1, 2);
		assertThat(pages.get(0).text()).contains("page one").doesNotContain("page two");
		assertThat(pages.get(1).text()).contains("Action items, page two.");
	}

	@Test
	void rejectsFileWhoseContentDoesNotMatchExtension() {
		byte[] notAPdf = "MZ this is really an executable".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> extractor.detect("brd.pdf", notAPdf))
				.isInstanceOf(UnsupportedDocumentException.class);
	}

	@Test
	void rejectsUnsupportedExtension() {
		assertThatThrownBy(() -> extractor.detect("notes.txt", "hello".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(UnsupportedDocumentException.class);
	}

	@Test
	void corruptPdfGivesClearError() {
		byte[] corrupt = "%PDF-1.7 garbage".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> extractor.extract(TextExtractor.Format.PDF, corrupt))
				.isInstanceOf(UnsupportedDocumentException.class)
				.hasMessageContaining("corrupt");
	}

}
