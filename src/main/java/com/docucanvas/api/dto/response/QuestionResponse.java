package com.docucanvas.api.dto.response;

import java.util.List;

public record QuestionResponse(
    String question,
    String answer,
    List<String> sources,
    int chunkCount,
    String imageUrl
) {}
