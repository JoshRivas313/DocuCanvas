package com.docucanvas.infrastructure.ai;

import com.docucanvas.application.chunk.ClusterName;
import com.docucanvas.application.port.out.ClusterNamingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nombra clusters semánticamente usando el LLM (Ollama) a partir de una muestra
 * de sus contenidos. Adaptador de infraestructura que implementa
 * {@link ClusterNamingPort}. Si el LLM falla, degrada a una heurística local.
 */
@Component
public class LlmClusterNamingAdapter implements ClusterNamingPort {

    private static final Logger log = LoggerFactory.getLogger(LlmClusterNamingAdapter.class);
    private static final int SAMPLE_CHUNKS = 3;
    private static final int SAMPLE_CHARS = 200;

    private final ChatClient chatClient;

    public LlmClusterNamingAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ClusterName name(int index, List<String> contents) {
        if (contents.isEmpty()) {
            return new ClusterName("Grupo " + index, "Sin contenido");
        }

        try {
            String sample = contents.stream()
                    .limit(SAMPLE_CHUNKS)
                    .map(s -> s.substring(0, Math.min(s.length(), SAMPLE_CHARS)))
                    .collect(Collectors.joining("\n--- \n"));

            String response = chatClient.prompt()
                    .system("Eres un experto en taxonomía. Analiza los fragmentos y responde exactamente en este formato:\n" +
                            "Nombre: [1-2 palabras]\n" +
                            "Descripción: [Una oración breve y profesional sobre el contenido]")
                    .user("Analiza estos fragmentos:\n" + sample)
                    .call()
                    .content();

            String name = extractField(response, "Nombre:").replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ\\s]", "").trim();
            String description = extractField(response, "Descripción:").trim();

            if (name.isEmpty()) return fallback(index, contents);

            String capitalized = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
            return new ClusterName("Grupo " + index + ": " + capitalized, description);

        } catch (Exception e) {
            log.warn("Fallo en IA para metadatos de cluster {}, usando fallback", index);
            return fallback(index, contents);
        }
    }

    private String extractField(String text, String label) {
        int start = text.indexOf(label);
        if (start == -1) return "";
        int end = text.indexOf("\n", start + label.length());
        if (end == -1) end = text.length();
        return text.substring(start + label.length(), end).trim();
    }

    private ClusterName fallback(int index, List<String> contents) {
        String longestWord = Arrays.stream(contents.get(0).split("\\s+"))
                .map(s -> s.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ]", ""))
                .filter(s -> s.length() > 4)
                .max(Comparator.comparingInt(String::length))
                .orElse("Tema " + index);

        String capitalized = longestWord.substring(0, 1).toUpperCase() + longestWord.substring(1).toLowerCase();
        return new ClusterName("Grupo " + index + ": " + capitalized,
                "Fragmentos relacionados con " + capitalized.toLowerCase() + ".");
    }
}
