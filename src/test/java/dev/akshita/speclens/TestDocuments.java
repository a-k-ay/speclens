package dev.akshita.speclens;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** Builds small real PDF/DOCX files in memory, so tests need no binary fixtures. */
public final class TestDocuments {

	private TestDocuments() {
	}

	/** One PDF page per argument. */
	public static byte[] pdf(String... pages) {
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (String text : pages) {
				PDPage page = new PDPage();
				pdf.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
					stream.beginText();
					stream.setFont(font, 10);
					stream.setLeading(12);
					stream.newLineAtOffset(50, 740);
					// PDF has no automatic line wrapping, so wrap by hand.
					for (String line : wrap(text, 95)) {
						stream.showText(line);
						stream.newLine();
					}
					stream.endText();
				}
			}
			pdf.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** One DOCX "page" per argument, separated by explicit page breaks. */
	public static byte[] docx(String... pages) {
		try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (int i = 0; i < pages.length; i++) {
				var run = docx.createParagraph().createRun();
				if (i > 0) {
					run.addBreak(BreakType.PAGE);
				}
				run.setText(pages[i]);
			}
			docx.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) {
				line.append(' ');
			}
			line.append(word);
		}
		lines.add(line.toString());
		return lines;
	}

}
