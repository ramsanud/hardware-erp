package com.hardware.erp.developer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What a developer actually needs to answer "which build is this box running
 * and how long has it been up".
 *
 * Every field here is chosen by name. There is no map of system properties,
 * no environment dump and no configuration echo, because those are exactly
 * where DB_PASSWORD, JWT_SECRET and APP_ENCRYPTION_KEY would surface. If a
 * future field needs adding, add it explicitly - never widen this to a
 * "return everything" shape.
 */
@Schema(description = "Named, non-sensitive runtime facts about this instance")
public record RuntimeDiagnosticsResponse(

        @Schema(example = "hardware-erp") String application,
        @Schema(example = "1.0.0") String version,
        @Schema(description = "Active Spring profiles, names only", example = "[\"local\"]")
        List<String> activeProfiles,

        @Schema(example = "21.0.1") String javaVersion,
        @Schema(example = "Linux") String osName,
        @Schema(description = "CPUs visible to the JVM", example = "8") int availableProcessors,

        @Schema(description = "Heap in use, MB", example = "312") long heapUsedMb,
        @Schema(description = "Heap ceiling, MB", example = "2048") long heapMaxMb,

        @Schema(description = "Seconds since this JVM started", example = "3641") long uptimeSeconds,
        @Schema(description = "Server clock, for diagnosing timezone drift against the client")
        OffsetDateTime serverTime,
        @Schema(example = "Asia/Kolkata") String serverTimeZone
) {
}
