import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * Renders the fictional sample documents in samples/source/*.txt into samples/*.pdf and *.docx.
 *
 * Source format: "---page---" starts a new page, "# " lines are headings, blank lines
 * separate paragraphs. The output name is the source name without ".txt".
 *
 * Run from the project root (see samples/README.md):
 *   ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   java -cp "$(cat target/cp.txt)" tools/GenerateSamples.java
 */
public class GenerateSamples {

	private static final String PAGE_BREAK = "---page---";
	private static final String FOOTER = "Fictional sample document for the SpecLens demo. Not a real client.";

	public static void main(String[] args) throws IOException {
		Path sourceDir = Path.of("samples/source");
		try (var files = Files.list(sourceDir)) {
			for (Path source : files.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
				String name = source.getFileName().toString().replaceFirst("\\.txt$", "");
				List<List<String>> pages = readPages(source);
				Path target = Path.of("samples", name);
				if (name.endsWith(".pdf")) {
					writePdf(pages, target);
				}
				else if (name.endsWith(".docx")) {
					writeDocx(pages, target);
				}
				else {
					throw new IllegalArgumentException("Unknown output type: " + name);
				}
				System.out.println("Wrote " + target + " (" + pages.size() + " pages)");
			}
		}
	}

	private static List<List<String>> readPages(Path source) throws IOException {
		List<List<String>> pages = new ArrayList<>();
		List<String> current = new ArrayList<>();
		for (String line : Files.readAllLines(source)) {
			if (line.strip().equals(PAGE_BREAK)) {
				pages.add(current);
				current = new ArrayList<>();
			}
			else {
				current.add(line);
			}
		}
		pages.add(current);
		return pages;
	}

	private static void writePdf(List<List<String>> pages, Path target) throws IOException {
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		float margin = 60;
		try (PDDocument pdf = new PDDocument()) {
			for (int p = 0; p < pages.size(); p++) {
				PDPage page = new PDPage(PDRectangle.A4);
				pdf.addPage(page);
				float width = page.getMediaBox().getWidth() - 2 * margin;
				float y = page.getMediaBox().getHeight() - margin;
				try (PDPageContentStream s = new PDPageContentStream(pdf, page)) {
					for (String line : pages.get(p)) {
						boolean heading = line.startsWith("# ");
						String text = heading ? line.substring(2) : line;
						PDType1Font font = heading ? bold : regular;
						float size = heading ? 13 : 10.5f;
						if (text.isBlank()) {
							y -= 8;
							continue;
						}
						for (String wrapped : wrap(text, font, size, width)) {
							y -= size * 1.45f;
							writeLine(s, font, size, margin, y, wrapped);
						}
						if (heading) {
							y -= 4;
						}
					}
					writeLine(s, regular, 8, margin, 30, FOOTER + "   Page " + (p + 1) + " of " + pages.size());
				}
			}
			pdf.save(target.toFile());
		}
	}

	private static void writeLine(PDPageContentStream s, PDType1Font font, float size, float x, float y, String text)
			throws IOException {
		s.beginText();
		s.setFont(font, size);
		s.newLineAtOffset(x, y);
		s.showText(text);
		s.endText();
	}

	/** Word-wrap using the font's real character widths. */
	private static List<String> wrap(String text, PDType1Font font, float size, float maxWidth) throws IOException {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else {
				line = new StringBuilder(candidate);
			}
		}
		lines.add(line.toString());
		return lines;
	}

	private static void writeDocx(List<List<String>> pages, Path target) throws IOException {
		try (XWPFDocument docx = new XWPFDocument(); FileOutputStream out = new FileOutputStream(target.toFile())) {
			for (int p = 0; p < pages.size(); p++) {
				boolean firstOnPage = true;
				for (String line : pages.get(p)) {
					if (line.isBlank()) {
						continue;
					}
					XWPFParagraph paragraph = docx.createParagraph();
					XWPFRun run = paragraph.createRun();
					if (p > 0 && firstOnPage) {
						run.addBreak(BreakType.PAGE);
					}
					firstOnPage = false;
					boolean heading = line.startsWith("# ");
					run.setBold(heading);
					run.setFontSize(heading ? 14 : 11);
					run.setText(heading ? line.substring(2) : line);
				}
			}
			XWPFRun footer = docx.createParagraph().createRun();
			footer.setFontSize(8);
			footer.setText(FOOTER);
			docx.write(out);
		}
	}

}
