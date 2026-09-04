package com.docucanvas.application.rag;

import com.docucanvas.application.question.RagInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * Obtiene del LLM una respuesta con forma de {@link RagInsight} — texto, prompt
 * visual y relaciones — en una única llamada.
 *
 * <p><b>Por qué se usa {@link BeanOutputConverter} explícitamente y no el atajo
 * {@code .call().entity(RagInsight.class)}:</b> el atajo hace exactamente esto
 * mismo por dentro (añade las instrucciones de formato al prompt, llama al
 * modelo, deserializa), pero si la deserialización falla lanza una excepción y
 * el texto crudo que devolvió el modelo se pierde. Con un modelo pequeño
 * ejecutándose en local — llama3.2 son 3B de parámetros — que la salida no sea
 * JSON válido no es un caso remoto: es un martes cualquiera.
 *
 * <p>Al hacer explícitos los dos pasos se conserva la respuesta cruda y se puede
 * degradar a "solo texto" en lugar de fallar la petición entera. El usuario
 * pierde el prompt visual y las relaciones, que son accesorios, y conserva la
 * respuesta, que es lo que pidió. Sin reintentar: un segundo turno del modelo
 * duplicaría la latencia justo en el caso en que ya ha ido mal.
 *
 * <p>Esto sustituye al bloque {@code [RELACIONES]} de texto plano que la versión
 * base extraía con expresiones regulares. La diferencia no es de estilo: el
 * esquema ahora se deriva del tipo Java, así que añadir un campo al record
 * actualiza a la vez las instrucciones que recibe el modelo y el binding de la
 * respuesta, sin ninguna regex que mantener en sincronía.
 */
@Component
public class StructuredInsightGenerator {

    private static final Logger log = LoggerFactory.getLogger(StructuredInsightGenerator.class);

    private final BeanOutputConverter<RagInsight> converter = new BeanOutputConverter<>(RagInsight.class);

    /**
     * @param chatClient cliente ya configurado con el system prompt
     * @param userPrompt prompt de usuario con contexto y pregunta
     * @param options    opciones portables del modelo
     * @return el insight estructurado, o una degradación a solo texto si el
     *         modelo no produjo un JSON válido
     */
    public RagInsight generate(ChatClient chatClient, String userPrompt, ChatOptions options) {
        String raw = chatClient.prompt()
                .options(options)
                .user(userPrompt + System.lineSeparator() + System.lineSeparator() + converter.getFormat())
                .call()
                .content();

        if (raw == null || raw.isBlank()) {
            log.warn("El modelo devolvió una respuesta vacía");
            return RagInsight.textOnly("");
        }

        try {
            RagInsight insight = converter.convert(raw);
            if (insight == null || insight.answer() == null || insight.answer().isBlank()) {
                log.warn("El JSON del modelo no contenía una respuesta utilizable; se degrada a texto plano");
                return RagInsight.textOnly(stripJsonArtifacts(raw));
            }
            return insight;
        } catch (Exception e) {
            log.warn("El modelo no devolvió un JSON válido ({}); se conserva la respuesta como texto plano",
                    e.getMessage());
            return RagInsight.textOnly(stripJsonArtifacts(raw));
        }
    }

    /**
     * Limpieza mínima para que una salida JSON a medio formar siga siendo legible
     * como texto: quita las vallas de bloque de código que muchos modelos añaden
     * aunque se les pida que no lo hagan.
     */
    private String stripJsonArtifacts(String raw) {
        return raw.replaceAll("(?s)```(?:json)?", "").trim();
    }
}
