package com.hardware.erp.supplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "SupplierContactRequest")
public record SupplierContactRequest(

        @Schema(example = "Ramesh Kumar")
        @NotBlank(message = "Contact name is required")
        @Size(max = 200) String contactName,

        @Schema(example = "Sales Manager")
        @Size(max = 100) String designation,

        @Schema(example = "9842011223")
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$",
                 message = "Enter a valid 10-digit Indian mobile number")
        String mobileNo,

        @Schema(example = "ramesh@sribalajihardware.in")
        @Email(message = "Enter a valid email address")
        @Size(max = 255) String email,

        @Schema(description = "Only one contact per supplier may be primary.",
                example = "true")
        boolean primary
) {}
