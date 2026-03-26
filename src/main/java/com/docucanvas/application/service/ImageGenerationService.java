package com.docucanvas.application.service;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageGenerationService.class);
    
    public ImagePrompt generateImagePrompt(String lLMAnswer) {
        log.info("Generando ImagePrompt visual para la respuesta de RAG");
        
        // Transformamos la respuesta densa del LLM en un prompt visual corto (Prompt Engineering dinámico)
        String baseTexto = lLMAnswer.length() > 200 ? lLMAnswer.substring(0, 200) : lLMAnswer;
        String visualPrompt = "Crea un diagrama de arquitectura inspirado en esto: " + baseTexto;
        
        log.debug("Visual prompt generado: {}", visualPrompt);

        // Devolvemos el prompt de imagen listo para ser ejecutado
        return new ImagePrompt(visualPrompt);
    }
}
