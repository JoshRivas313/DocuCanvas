package com.docucanvas.api.dto.response;

import java.util.List;

public record RetrievalInsightsDTO(
    String confidenceLevel,
    double confidenceScore,
    List<String> keyConcepts,
    List<ConceptRelationDTO> conceptRelations,
    List<String> documentsUsed,
    int totalChunksAnalyzed,
    String howItWasFound,
    boolean partialMatch
) {}
