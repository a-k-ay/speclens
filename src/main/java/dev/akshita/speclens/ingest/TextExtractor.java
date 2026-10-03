package dev.akshita.speclens.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;
import org.springframework.stereotype.Component;

/** Turns an uploaded PDF or DOCX into text per page. */
@Component
public class TextExtractor {

	public enum Format {

		PDF("application/pdf"),
		DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

		public final String contentType;

		Format(String contentType) {
			this.contentType = contentType;
		}

	}

	/** Decide the format from the extension AND the file's first bytes, so a renamed file is rejected. */
	public Format detect(String filename, byte[] bytes) {
		String name = filename.toLowerCase(Locale.ROOT);
		if (name.endsWith(".pdf") && startsWith(bytes, "%PDF-")) {
			return Format.PDF;
		}
		// DOCX is a ZIP archive, and every ZIP starts with "PK".
		if (name.endsWith(".docx") && startsWith(bytes, "PK")) {
			return Format.DOCX;
		}
		throw new UnsupportedDocumentException("Only PDF and DOCX files are supported");
	}

	public List<PageText> extract(Format format, byte[] bytes) {
		try {
			return switch (format) {
				case PDF -> extractPdf(bytes);
				case DOCX -> extractDocx(bytes);
			};
		}
		catch (UnsupportedDocumentException ex) {
			throw ex;
		}
		catch (InvalidPasswordException ex) {
			throw new UnsupportedDocumentException("Password-protected PDFs are not supported", ex);
		}
		// PDFBox and POI throw a mix of IOException and runtime exceptions on corrupt files.
		catch (IOException | RuntimeException ex) {
			throw new UnsupportedDocumentException("Could not read the file; it may be corrupt", ex);
		}
	}

	private List<PageText> extractPdf(byte[] bytes) throws IOException {
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			// Read text in visual order (top-to-bottom, left-to-right), not file order.
			stripper.setSortByPosition(true);
			List<PageText> pages = new ArrayList<>();
			for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				pages.add(new PageText(page, stripper.getText(pdf)));
			}
			return pages;
		}
	}

	/**
	 * DOCX has no stored pages; Word computes them when it renders. When Word saves a file
	 * it records where each page started (w:lastRenderedPageBreak), so we use those markers
	 * if present and fall back to explicit page breaks otherwise. Page numbers for DOCX are
	 * therefore "as last rendered by Word", which is documented in docs/adr/0004.
	 */
	private List<PageText> extractDocx(byte[] bytes) throws IOException {
		try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
			boolean useRenderedBreaks = docx.getDocument().xmlText().contains("lastRenderedPageBreak");
			DocxPager pager = new DocxPager();
			for (IBodyElement element : docx.getBodyElements()) {
				if (element instanceof XWPFParagraph paragraph) {
					if (!useRenderedBreaks && paragraph.isPageBreak()) {
						pager.newPage();
					}
					for (XWPFRun run : paragraph.getRuns()) {
						int breaks = useRenderedBreaks
								? run.getCTR().sizeOfLastRenderedPageBreakArray()
								: countExplicitPageBreaks(run);
						for (int i = 0; i < breaks; i++) {
							pager.newPage();
						}
						pager.append(run.text());
					}
					pager.append("\n");
				}
				else if (element instanceof XWPFTable table) {
					pager.append(table.getText()).append("\n");
				}
			}
			return pager.pages();
		}
	}

	private static int countExplicitPageBreaks(XWPFRun run) {
		int count = 0;
		for (CTBr br : run.getCTR().getBrList()) {
			if (br.isSetType() && br.getType() == STBrType.PAGE) {
				count++;
			}
		}
		return count;
	}

	private static boolean startsWith(byte[] bytes, String prefix) {
		if (bytes.length < prefix.length()) {
			return false;
		}
		for (int i = 0; i < prefix.length(); i++) {
			if (bytes[i] != prefix.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	/** Collects DOCX text into pages as breaks are encountered. */
	private static final class DocxPager {

		private final List<PageText> pages = new ArrayList<>();
		private StringBuilder current = new StringBuilder();

		void newPage() {
			pages.add(new PageText(pages.size() + 1, current.toString()));
			current = new StringBuilder();
		}

		StringBuilder append(String text) {
			return current.append(text);
		}

		List<PageText> pages() {
			pages.add(new PageText(pages.size() + 1, current.toString()));
			return pages;
		}

	}

}
