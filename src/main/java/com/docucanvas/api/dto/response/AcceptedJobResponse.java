package com.docucanvas.api.dto.response;

import com.docucanvas.domain.model.DocumentStatus;
import java.util.UUID;

public record AcceptedJobResponse(
    UUID jobId,
    UUID documentId,
    DocumentStatus status,
    String statusUrl
) {}
