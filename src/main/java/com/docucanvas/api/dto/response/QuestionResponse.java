package com.docucanvas.api.dto.response;

import java.util.List;

public record QuestionResponse(
    String question,
    String answer,
    List<CitationDTO> citations,
    int chunkCount,
    String imageUrl,
    long retrievalTimeMs,
    long generationTimeMs,
    long imageTimeMs
) {}
