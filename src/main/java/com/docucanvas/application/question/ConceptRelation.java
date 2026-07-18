package com.docucanvas.application.question;

import java.util.List;

/** Un concepto principal y los subtemas con los que aparece relacionado en el contexto recuperado. */
public record ConceptRelation(String concept, List<String> relatedTopics) {}
