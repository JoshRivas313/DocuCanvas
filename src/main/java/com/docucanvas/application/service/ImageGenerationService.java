package com.docucanvas.application.service;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
public class ImageGenerationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageGenerationService.class);
    private final ImageModel imageModel;

    public ImageGenerationService(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    public String generateImage(String ragAnswer) {
        log.info("Generando prompt visual basado en respuesta RAG");
        
        // Transformamos la respuesta en un prompt visual (simplificado para la demo)
        String visualPrompt = String.format(
            "A professional, modern, 3D isometric illustration representing: %s. " +
            "Cinematic lighting, high detail, digital art style, vibrant colors.", 
            ragAnswer.length() > 200 ? ragAnswer.substring(0, 200) : ragAnswer
        );

        try {
            ImageResponse response = imageModel.call(
                new ImagePrompt(visualPrompt,
                    OpenAiImageOptions.builder()
                        .withQuality("hd")
                        .withN(1)
                        .withHeight(1024)
                        .withWidth(1024)
                        .build())
            );
            
            String imageUrl = response.getResult().getOutput().getUrl();
            log.info("Imagen generada exitosamente: {}", imageUrl);
            return imageUrl;
            
        } catch (Exception e) {
            log.error("Error al generar imagen con ImageModel", e);
            // Fallback para no romper la demo (una imagen placeholder elegante)
            return "https://images.unsplash.com/photo-1620712943543-bcc4688e7485?auto=format&fit=crop&q=80&w=1000";
        }
    }
}
