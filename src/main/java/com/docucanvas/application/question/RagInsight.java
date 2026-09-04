package com.docucanvas.application.question;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Contrato estructurado que el LLM devuelve en <b>una sola llamada</b>: la
 * respuesta textual, el prompt visual derivado de ella y las relaciones entre
 * conceptos.
 *
 * <p><b>Por qué existe este record:</b> en la versión base, las relaciones entre
 * conceptos se pedían como un bloque de texto plano al final de la respuesta
 * ({@code [RELACIONES]\nConcepto: a, b}) y se extraían con una expresión regular.
 * Cualquier desviación del formato — una mayúscula distinta, un salto de línea
 * de más, dos puntos dentro del nombre de un concepto — rompía el parseo en
 * silencio. Declarar la forma de la respuesta como un tipo Java y dejar que
 * Spring AI genere las instrucciones de formato y haga el binding elimina esa
 * clase entera de fallos.
 *
 * <p><b>Y por qué el prompt visual viaja aquí dentro:</b> es la pieza que hace
 * que el flujo sea realmente multimodal. Si la imagen se generase a partir del
 * contexto crudo — como ocurría en la versión base, en un hilo paralelo e
 * independiente — no estaría representando lo que el modelo entendió, sino las
 * palabras más frecuentes del texto recuperado. Al pedir {@code visualPrompt}
 * en la misma llamada que produce {@code answer}, la imagen queda anclada a la
 * misma evidencia y a la misma interpretación que la respuesta textual.
 *
 * @param answer       respuesta al usuario, fundamentada solo en el contexto recuperado
 * @param visualPrompt descripción visual para el modelo de imagen, derivada del contexto
 * @param relations    conceptos clave y sus subtemas, tal como los identificó el modelo
 */
@JsonClassDescription("Respuesta fundamentada en documentos, con su representación visual")
public record RagInsight(

        @JsonProperty(required = true)
        @JsonPropertyDescription("Respuesta a la pregunta, en el mismo idioma en que fue formulada, "
                + "usando exclusivamente la información del contexto proporcionado")
        String answer,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Descripción en inglés de un diagrama conceptual que represente "
                + "las ideas del contexto: entidades, relaciones y estructura. Sin texto ni "
                + "letras en la imagen. Solo conceptos que aparezcan realmente en el contexto")
        String visualPrompt,

        @JsonPropertyDescription("Conceptos clave del contexto y los subtemas asociados a cada uno")
        List<ConceptRelation> relations) {

    /**
     * Resultado degradado cuando el modelo no produce un JSON válido. Mantener la
     * respuesta textual y renunciar solo a lo accesorio es preferible a fallar la
     * petición entera: el usuario sigue obteniendo lo que pidió.
     */
    public static RagInsight textOnly(String answer) {
        return new RagInsight(answer, null, List.of());
    }

    public boolean hasVisualPrompt() {
        return visualPrompt != null && !visualPrompt.isBlank();
    }

    public List<ConceptRelation> relationsOrEmpty() {
        return relations != null ? relations : List.of();
    }
}
