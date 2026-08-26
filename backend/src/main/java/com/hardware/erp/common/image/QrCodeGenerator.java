package com.hardware.erp.common.image;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** UPI payment QR for the invoice PDF (CR-023 follow-up) - generated at render time, never stored. */
public final class QrCodeGenerator {

    private static final int SIZE_PX = 240;

    private QrCodeGenerator() {
    }

    /** payeeVpa e.g. "shopname@okicici". amountRupees may be null to let the payer enter their own amount. */
    public static byte[] upiPaymentQrPng(String payeeVpa, String payeeName, String note, java.math.BigDecimal amountRupees) {
        StringBuilder upiUri = new StringBuilder("upi://pay?pa=")
                .append(encode(payeeVpa))
                .append("&pn=").append(encode(payeeName))
                .append("&cu=INR");
        if (note != null && !note.isBlank()) {
            upiUri.append("&tn=").append(encode(note));
        }
        if (amountRupees != null) {
            upiUri.append("&am=").append(encode(amountRupees.toPlainString()));
        }
        return pngBytes(upiUri.toString());
    }

    private static byte[] pngBytes(String content) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new UncheckedIOException("Failed to render UPI QR code", new IOException(e));
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
