package com.docucanvas.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TextImportRequest(
    @NotBlank String title,
    @NotBlank String content,
    String sourceType
) {}
