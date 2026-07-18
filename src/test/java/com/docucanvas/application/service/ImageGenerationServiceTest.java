package com.docucanvas.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link ImageGenerationService} centrados en la detección de tema.
 *
 * <p>Caja negra deliberada: se decodifica el SVG resultante y se verifica el
 * título de sección que cada plantilla imprime, en vez de exponer
 * {@code detectTopic} solo para hacerlo testeable.
 */
@DisplayName("ImageGenerationService — Selección de plantilla por tema")
class ImageGenerationServiceTest {

    private final ImageGenerationService service = new ImageGenerationService(new ConceptExtractor());

    private String decodedSvg(String dataUrl) {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Contexto sobre blockchain elige la plantilla de cadena de bloques")
    void detectaTemaBlockchain() {
        String svg = decodedSvg(service.generateImageDataUrl("pregunta",
                "El sistema usa blockchain y hash criptográfico para validar cada transacción en la cadena."));

        assertThat(svg).contains("CADENA DE BLOQUES");
    }

    @Test
    @DisplayName("Contexto sobre Kubernetes/cloud elige la plantilla de infraestructura cloud")
    void detectaTemaCloud() {
        String svg = decodedSvg(service.generateImageDataUrl("pregunta",
                "El despliegue usa Kubernetes con varios pods en un cluster sobre AWS, gestionados por contenedores Docker."));

        assertThat(svg).contains("INFRAESTRUCTURA CLOUD");
    }

    @Test
    @DisplayName("Contexto sobre gobierno de TI elige la plantilla de modelo de gobierno")
    void detectaTemaGobiernoTi() {
        String svg = decodedSvg(service.generateImageDataUrl("pregunta",
                "El modelo de Gobierno de TI se basa en COBIT 5, ITIL v4 y CMMI para medir KPI de gobernanza."));

        assertThat(svg).contains("MODELO DE GOBIERNO");
    }

    @Test
    @DisplayName("Contexto sin palabras clave de ningún tema cae en la plantilla genérica")
    void sinTemaDetectadoUsaGenerica() {
        String svg = decodedSvg(service.generateImageDataUrl("pregunta",
                "Texto neutro sobre jardinería, plantas y riego semanal en primavera."));

        assertThat(svg).contains("CONCEPTOS CLAVE").doesNotContain("CADENA DE BLOQUES", "INFRAESTRUCTURA CLOUD");
    }

    @Test
    @DisplayName("El SVG generado siempre es un documento bien formado con la pregunta en el header")
    void svgBienFormadoConPregunta() {
        String svg = decodedSvg(service.generateImageDataUrl("¿Qué trata el documento?", "contenido cualquiera"));

        assertThat(svg).startsWith("<svg").endsWith("</svg>");
        assertThat(svg).contains("¿Qué trata el documento?");
    }
}
