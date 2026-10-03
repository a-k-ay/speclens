package dev.akshita.speclens.document;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/**
 * Draws one PDF page as a PNG, so a citation can show the page exactly as it looks
 * (tables, columns, headers) rather than only the extracted text.
 */
@Component
public class PageRenderer {

	/** Sharp enough to read table text on screen; an A4 page is about 900 x 1300 pixels. */
	private static final float DPI = 110;

	/** Rendering uses memory and CPU; on a 512 MB instance, draw at most two pages at once. */
	private final Semaphore permits = new Semaphore(2);

	public byte[] renderPng(byte[] pdf, int pageNumber) {
		permits.acquireUninterruptibly();
		try (PDDocument document = Loader.loadPDF(pdf)) {
			if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
				throw new PreviewNotAvailableException("The document has no page " + pageNumber);
			}
			BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageNumber - 1, DPI, ImageType.RGB);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			permits.release();
		}
	}

}
