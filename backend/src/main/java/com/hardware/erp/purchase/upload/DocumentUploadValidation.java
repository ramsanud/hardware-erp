package com.hardware.erp.purchase.upload;

import com.hardware.erp.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Every uploaded supplier bill is untrusted input (spec §20) - checked
 * here before a single byte reaches a parser. Unlike ImageValidation
 * (which only checks the client-reported content type), this also
 * verifies the actual file signature for the one format where a
 * reliable signature exists (.xlsx is a ZIP container, always starting
 * "PK\x03\x04") - a mismatched extension is rejected outright rather
 * than handed to Apache POI's XML parser.
 */
public final class DocumentUploadValidation {

    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "xlsx");
    private static final byte[] ZIP_SIGNATURE = { 0x50, 0x4B, 0x03, 0x04 };

    private DocumentUploadValidation() {
    }

    public static String validateAndGetExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(
                    "File must be " + (MAX_BYTES / (1024 * 1024)) + "MB or smaller",
                    HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TOO_LARGE");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            // Path-traversal guard - a filename is stored for display only, never used to build a filesystem path, but a client sending one that looks like a path is not a bill.
            throw new BusinessException("Invalid file name", HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILENAME");
        }
        if (filename.indexOf('"') >= 0 || filename.indexOf('\r') >= 0 || filename.indexOf('\n') >= 0) {
            // This filename is later echoed into a Content-Disposition response header on download -
            // a quote or CR/LF here could break out of the quoted filename param or inject a header.
            throw new BusinessException("Invalid file name", HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILENAME");
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "Automatic extraction only supports CSV and Excel (.xlsx) files in this version. "
                    + "PDF, image and scanned-document import need a configured OCR/AI provider - "
                    + "enter this purchase manually instead, or export the bill as CSV/Excel first.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_FILE_TYPE");
        }
        if (extension.equals("xlsx")) {
            byte[] header = firstBytes(file, 4);
            if (header.length < 4 || !matches(header, ZIP_SIGNATURE)) {
                throw new BusinessException(
                        "This file's contents don't match a .xlsx file, even though it's named like one",
                        HttpStatus.UNPROCESSABLE_ENTITY, "FILE_SIGNATURE_MISMATCH");
            }
        }
        return extension;
    }

    /**
     * The content type a downloaded document is served with must never come from the
     * client's declared multipart Content-Type - that header is fully attacker-controlled,
     * and a "bill.csv" declared as text/html would render as live HTML (stored XSS) when
     * opened inline. Derive it instead from the extension this class already validated.
     */
    public static String safeContentType(String extension) {
        return switch (extension) {
            case "csv" -> "text/csv";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    private static byte[] firstBytes(MultipartFile file, int count) {
        try (var input = file.getInputStream()) {
            byte[] buffer = new byte[count];
            int read = input.readNBytes(buffer, 0, count);
            return read == count ? buffer : new byte[0];
        } catch (Exception e) {
            throw new BusinessException("Could not read the uploaded file");
        }
    }

    private static boolean matches(byte[] actual, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) return false;
        }
        return true;
    }
}
