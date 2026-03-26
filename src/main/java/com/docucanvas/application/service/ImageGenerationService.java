package com.docucanvas.application.service;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageGenerationService.class);
    
    // Inyectamos la interfaz ImageModel de Spring AI
    // En la ponencia, esto demostraría la integración con DALL-E o Imagen (Vertex AI)
    private final ImageModel imageModel;

    public ImageGenerationService(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    public String generateImage(String lLMAnswer) {
        log.info("Generando representación visual vía ImageModel (Spring AI) basado en la respuesta del LLM");
        
        try {
            // Transformamos la respuesta densa del LLM en un prompt visual corto (Prompt Engineering dinámico)
            String visualPrompt = "A futuristic minimalist representation of: " + lLMAnswer.substring(0, Math.min(100, lLMAnswer.length()));
            
            log.debug("Visual prompt enviado a ImageModel: {}", visualPrompt);

            // Llamada estándar de Spring AI
            ImageResponse response = imageModel.call(new ImagePrompt(visualPrompt));
            
            String imageUrl = response.getResult().getOutput().getUrl();
            log.info("Imagen generada exitosamente: {}", imageUrl);
            
            return imageUrl;
            
        } catch (Exception e) {
            log.error("Fallo de ImageModel. Usando fallback visual de alta calidad.");
            // Fallback para la demo en caso de falta de créditos o error de red
            return getBase64RealDocuCanvas();
        }
    }
    
    public String getBase64RealDocuCanvas() {
        // SVG minimalista que representa el conocimiento generado por DocuCanvas
        return "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNTEyIiBoZWlnaHQ9IjUxMiIgdmlld0JveD0iMCAwIDUxMiA1MTIiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjUxMiIgaGVpZ2h0PSI1MTIiIHJ4PSI0MCIgZmlsbD0iIzBGMTcyQSIvPjxjaXJjbGUgY3g9IjI1NiIgY3k9IjI1NiIgcj0iMTYwIiBmaWxsPSJ1cmwoI2FwaV9ncmFkaWVudCkiIGZpbGwtb3BhY2l0eT0iMC4yIi8+PGRlZnM+PGxpbmVhckdyYWRpZW50IGlkPSJhcGlfZ3JhZGllbnQiIHgxPSI5NiIgeTE9Ijk2IiB4Mj0iNDE2IiB5Mj0iNDE2IiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHN0b3Agc3RvcC1jb2xvcj0iIzM4QkRGNCIvPjxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzgxOENGOCIvPjwvbGluZWFyR3JhZGllbnQ+PC9kZWZzPjx0ZXh0IHg9IjE1MCIgeT0iMjcwIiBmaWxsPSJ3aGl0ZSIgZm9udC1mYWWlseT0iSW50ZXIsIHNhbnMtc2VyaWYiIGZvbnQtc2l6ZT0iMzIiIGZvbnQtd2VpZ2h0PSJib2xkIj5ESUdJVEFMIENBTlZBUzwvdGV4dD48L3N2Zz4=";
    }
}
