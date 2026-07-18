package com.docucanvas.api.dto.response;

import java.util.List;

public record ConceptRelationDTO(String concept, List<String> relatedTopics) {}
