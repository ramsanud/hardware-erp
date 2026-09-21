package com.hardware.erp.report.export;

import com.hardware.erp.report.export.ReportDocument.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-101. Asserts the actual raster, not just a magic-byte prefix: the
 * width matches what was asked for (openhtmltopdf-pdfbox's own page size
 * multiplied by the computed scale), and PNG/JPEG both decode.
 */
class DocumentImageRendererTest {

    private final ReportExporter exporter = new ReportExporter();
    private final DocumentImageRenderer renderer = new DocumentImageRenderer(exporter);

    private static ReportDocument sample() {
        return ReportDocument.builder("Rate List")
                .caption("Sara Hardware & Sons")
                .table(null,
                        List.of(Column.text("Item"), Column.money("Price")),
                        List.of(List.of("Godrej Duplex Lock 70mm", "550.00"),
                                List.of("Anchor Switch 6A", "45.00")),
                        List.of("Total", "595.00"))
                .build();
    }

    @Test
    @DisplayName("the PNG decodes and is exactly as wide as requested")
    void pngHasRequestedWidth() throws Exception {
        byte[] png = renderer.toPng(sample(), 800);
        assertThat(png).startsWith(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

    @Test
    @DisplayName("the default width is the WhatsApp-friendly 1080px")
    void defaultWidthMatchesConstant() throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(renderer.toPng(sample())));
        assertThat(image.getWidth()).isEqualTo(DocumentImageRenderer.DEFAULT_WIDTH_PX);
    }

    @Test
    @DisplayName("JPEG decodes at the requested width - a lower quality still produces a valid, decodable file")
    void jpegDecodesAtRequestedWidth() throws Exception {
        // Not asserted smaller than the PNG: a sparse table on a white
        // background is exactly the content JPEG's block compression can
        // lose to PNG's lossless one on, so file-size ordering between the
        // two formats is not a safe invariant for this kind of document.
        byte[] jpeg = renderer.toJpeg(sample(), 800, 0.6f);
        assertThat(jpeg).startsWith(new byte[]{(byte) 0xFF, (byte) 0xD8});
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(image.getWidth()).isEqualTo(800);
    }

    @Test
    @DisplayName("a width outside the sane range is refused before any PDF is even rendered")
    void widthIsBounded() {
        assertThatThrownBy(() -> renderer.toPng(sample(), 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renderer.toPng(sample(), 5000)).isInstanceOf(IllegalArgumentException.class);
    }
}
