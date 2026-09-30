package com.hardware.erp.report.export;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * CR-101. Turns the exact PDF {@link ReportExporter} already builds into a
 * PNG or JPEG, so a shared image and a downloaded PDF are pixel-identical -
 * one template, three formats. Pipeline: {@link ReportDocument} -> the same
 * XHTML {@link ReportExporter#buildHtml} feeds the PDF renderer -> PDF bytes
 * -> PDFBox's {@link PDFRenderer} rasterizes a page to a Java2D
 * {@link BufferedImage} -> PNG/JPEG bytes.
 *
 * Only the first page is rasterized. Every report and voucher this app
 * renders is one continuous table with a repeating header (CSS Paged
 * Media), so a share image showing "page 1 of 3" would be a worse artefact
 * than a tall single page - stitching multi-page PDFs into one image is
 * left for a CR that actually needs it, not invented here.
 */
@Component
@RequiredArgsConstructor
public class DocumentImageRenderer {

    /** WhatsApp-friendly default - matches what the sharing target actually displays well. */
    public static final int DEFAULT_WIDTH_PX = 1080;

    /** Hard ceiling most chat apps will accept for an image attachment. */
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final ReportExporter exporter;

    public byte[] toPng(ReportDocument document) {
        return toPng(document, DEFAULT_WIDTH_PX);
    }

    public byte[] toPng(ReportDocument document, int widthPx) {
        BufferedImage image = rasterizeFirstPage(document, widthPx);
        byte[] png = encode(image, "png", 1f);
        // A wide/tall report table can still exceed the ceiling as a lossless
        // PNG; re-encoding the very same raster as JPEG is the one fallback
        // worth having here, not a multi-step downscale loop for a case this
        // pipeline's actual documents (short tabular reports) do not hit.
        return png.length <= MAX_BYTES ? png : encode(image, "jpg", 0.85f);
    }

    public byte[] toJpeg(ReportDocument document, int widthPx, float quality) {
        return encode(rasterizeFirstPage(document, widthPx), "jpg", quality);
    }

    private BufferedImage rasterizeFirstPage(ReportDocument document, int widthPx) {
        if (widthPx < 200 || widthPx > 4000) {
            throw new IllegalArgumentException("widthPx must be between 200 and 4000");
        }
        byte[] pdfBytes = exporter.toPdf(document);
        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            PDPage page = pdf.getPage(0);
            float pageWidthPts = page.getMediaBox().getWidth();
            float scale = widthPx / pageWidthPts;
            PDFRenderer renderer = new PDFRenderer(pdf);
            return renderer.renderImage(0, scale, ImageType.RGB);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to rasterize the report PDF to an image", e);
        }
    }

    private static byte[] encode(BufferedImage image, String formatName, float quality) {
        try {
            if ("jpg".equals(formatName)) {
                return encodeJpeg(image, quality);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, formatName, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode the report image", e);
        }
    }

    /** ImageIO.write(image, "jpg", out) ignores quality entirely - the writer's own param has to be set. */
    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG ImageWriter available on this JVM");
        }
        ImageWriter writer = writers.next();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                // JPEG has no alpha channel; a BufferedImage from PDFRenderer's
                // ImageType.RGB already has none, but TYPE_INT_ARGB would fail
                // the writer outright, so this is a real guard, not decoration.
                BufferedImage rgb = image.getColorModel().hasAlpha() ? withoutAlpha(image) : image;
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage withoutAlpha(BufferedImage source) {
        BufferedImage opaque = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        opaque.createGraphics().drawImage(source, 0, 0, null);
        return opaque;
    }
}
