package com.docucanvas.application.service;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Service;

/**
 * Servicio responsable de transformar la respuesta textual del LLM en un
 * prompt optimizado para generación de imágenes con DALL-E (ImageModel).
 *
 * <p>Este es el puente entre la fase RAG y la fase visual del pipeline.
 */
@Service
public class ImageGenerationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageGenerationService.class);

    // Máximo de caracteres de contexto que enviamos al modelo de imagen
    private static final int MAX_CONTEXT_LENGTH = 300;

    /**
     * Genera un {@link ImagePrompt} altamente contextualizado a partir de la
     * respuesta producida por el pipeline RAG.
     *
     * @param llmAnswer respuesta del LLM enriquecida con contexto documental
     * @return prompt listo para ser ejecutado por {@link org.springframework.ai.image.ImageModel}
     */
    public ImagePrompt generateImagePrompt(String llmAnswer) {
        return buildPrompt(llmAnswer);
    }

    /**
     * Genera un {@link ImagePrompt} basado en la pregunta original y el contexto
     * documental recuperado, permitiendo ejecución en paralelo con el LLM.
     *
     * @param question  pregunta original del usuario
     * @param context   contexto documental recuperado del VectorStore
     * @return prompt listo para ser ejecutado por {@link org.springframework.ai.image.ImageModel}
     */
    public ImagePrompt generateImagePromptFromContext(String question, String context) {
        log.info("Construyendo ImagePrompt visual a partir de contexto directo (paralelo)");
        String combined = "Pregunta: " + question + ". Contexto: " + context;
        return buildPrompt(combined);
    }

    private ImagePrompt buildPrompt(String sourceText) {
        log.info("Construyendo ImagePrompt visual");

        // 1. Extraemos el fragmento más representativo
        String contexto = sourceText.length() > MAX_CONTEXT_LENGTH
                ? sourceText.substring(0, MAX_CONTEXT_LENGTH)
                : sourceText;

        // 2. Prompt engineering dinámico: contexto + estilo profesional para la demo
        String visualPrompt = String.format(
                "Ilustración técnica profesional y minimalista que represente visualmente "
              + "los siguientes conceptos: %s. "
              + "Estilo: infografía moderna con íconos planos, paleta azul y blanca, "
              + "tipografía sans-serif, fondo blanco limpio, sin texto en la imagen.",
                contexto);

        log.debug("Visual prompt generado ({} caracteres): {}", visualPrompt.length(), visualPrompt);

        // 3. Opciones: quality standard para velocidad óptima en demos en vivo
        ImageOptions options = OpenAiImageOptions.builder()
                .model("dall-e-3")
                .quality("standard")
                .height(1024)
                .width(1024)
                .build();

        return new ImagePrompt(visualPrompt, options);
    }
}
