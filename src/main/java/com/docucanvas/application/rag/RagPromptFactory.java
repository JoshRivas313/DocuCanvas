package com.docucanvas.application.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Construye los prompts del pipeline RAG.
 *
 * <p><b>Por qué es una clase aparte:</b> el prompt es el artefacto que más se
 * itera en un sistema RAG y el que más determina la calidad del resultado. En la
 * versión base vivía como constantes y concatenaciones de {@code String} dentro
 * de un servicio de 385 líneas que además hacía retrieval, concurrencia y
 * parseo. Aislarlo lo vuelve legible, testeable y — lo que importa en una
 * charla — mostrable en pantalla sin acompañarlo de todo lo demás.
 *
 * <p>Se usa {@link PromptTemplate} en lugar de concatenar cadenas: los huecos
 * quedan marcados explícitamente y el renderizado falla si falta una variable,
 * en vez de producir en silencio un prompt con un trozo vacío.
 */
@Component
public class RagPromptFactory {

    private static final String SYSTEM_PROMPT = """
            Eres un asistente de DocuCanvas especializado en analizar documentos indexados.

            INSTRUCCIONES:
            1. Usa EXCLUSIVAMENTE el CONTEXTO proporcionado para responder. No inventes datos.
            2. Si el contexto contiene datos concretos (números, fechas, nombres), cítalos textualmente.
            3. Si el contexto es suficiente para sintetizar o resumir, hazlo de forma clara y organizada.
            4. Si el contexto es solo parcialmente relevante para la pregunta (temas relacionados
               pero que no la responden de forma directa), dilo explícitamente con la frase
               "La documentación contiene información relacionada, aunque no responde de forma
               explícita la pregunta." y luego resume lo que sí dice el contexto sobre el tema.
            5. Responde en el mismo idioma de la pregunta.
            6. Al final de tu respuesta, en una línea nueva, agrega SIEMPRE un bloque con este
               formato exacto, usando los "Conceptos detectados" que se te dan (no inventes otros):
               [RELACIONES]
               Concepto1: subtema a, subtema b, subtema c
               Concepto2: subtema d, subtema e
            """;

    private static final PromptTemplate USER_TEMPLATE = new PromptTemplate("""
            Contexto:
            {context}

            Conceptos detectados: {concepts}

            Pregunta: {question}
            """);

    private static final String NO_CONTEXT = "No se encontró contexto relevante en los documentos.";
    private static final String NO_CONCEPTS = "(ninguno detectado)";

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** Une el texto de los chunks recuperados en el bloque de contexto del prompt. */
    public String buildContext(List<Document> documents) {
        return documents.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    public String buildUserPrompt(String context, List<String> keyConcepts, String question) {
        return USER_TEMPLATE.render(Map.of(
                "context", context == null || context.isBlank() ? NO_CONTEXT : context,
                "concepts", keyConcepts == null || keyConcepts.isEmpty()
                        ? NO_CONCEPTS : String.join(", ", keyConcepts),
                "question", question));
    }
}
