package com.docucanvas.infrastructure.ai;

import com.docucanvas.application.chunk.ClusterName;
import com.docucanvas.application.port.out.ClusterNamingPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nombra clusters semánticamente usando el LLM (Ollama) a partir de una muestra
 * de sus contenidos. Adaptador de infraestructura que implementa
 * {@link ClusterNamingPort}. Si el LLM falla, degrada a una heurística local.
 *
 * <p><b>Caché por firma de contenido:</b> el resultado del LLM se cachea con la
 * propia muestra como clave, independiente del índice de cluster. Así, cuando la
 * ingesta de un documento invalida las cachés de visualización ({@code chunksGlobal}
 * / {@code chunksDoc}), los clusters cuyo contenido no cambió reutilizan su nombre
 * sin volver a llamar al LLM — y un mismo cluster nombrado en la vista por documento
 * se reutiliza en la vista global (donde su índice es distinto). Los resultados del
 * fallback heurístico NO se cachean, para que un fallo transitorio del LLM pueda
 * reintentarse en la siguiente visualización.
 */
@Component
public class LlmClusterNamingAdapter implements ClusterNamingPort {

    private static final Logger log = LoggerFactory.getLogger(LlmClusterNamingAdapter.class);
    private static final int SAMPLE_CHUNKS = 3;
    private static final int SAMPLE_CHARS = 200;

    /** Nombre + descripción producidos por el LLM, sin el prefijo dependiente del índice. */
    private record NameCore(String name, String description) {}

    private final ChatClient chatClient;
    // La clave es la muestra (≤600 chars, ≤500 entradas): memoria acotada y sin
    // riesgo de colisión de hashes.
    private final Cache<String, NameCore> nameCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(6))
            .build();

    public LlmClusterNamingAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ClusterName name(int index, List<String> contents) {
        if (contents.isEmpty()) {
            return new ClusterName("Grupo " + index, "Sin contenido");
        }

        String sample = buildSample(contents);

        NameCore cached = nameCache.getIfPresent(sample);
        if (cached != null) {
            log.debug("Nombre de cluster {} resuelto desde caché (firma de contenido)", index);
            return compose(index, cached);
        }

        try {
            NameCore core = askLlm(sample);
            if (core == null) {
                return fallback(index, contents);
            }
            nameCache.put(sample, core);
            return compose(index, core);
        } catch (Exception e) {
            log.warn("Fallo en IA para metadatos de cluster {}, usando fallback", index);
            return fallback(index, contents);
        }
    }

    private String buildSample(List<String> contents) {
        return contents.stream()
                .limit(SAMPLE_CHUNKS)
                .map(s -> s.substring(0, Math.min(s.length(), SAMPLE_CHARS)))
                .collect(Collectors.joining("\n--- \n"));
    }

    /** Llama al LLM y parsea el resultado; devuelve {@code null} si la respuesta es inutilizable. */
    private NameCore askLlm(String sample) {
        String response = chatClient.prompt()
                .system("Eres un experto en taxonomía. Analiza los fragmentos y responde exactamente en este formato:\n" +
                        "Nombre: [1-2 palabras]\n" +
                        "Descripción: [Una oración breve y profesional sobre el contenido]")
                .user("Analiza estos fragmentos:\n" + sample)
                .call()
                .content();

        String name = extractField(response, "Nombre:").replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ\\s]", "").trim();
        String description = extractField(response, "Descripción:").trim();

        if (name.isEmpty()) return null;

        String capitalized = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        return new NameCore(capitalized, description);
    }

    private ClusterName compose(int index, NameCore core) {
        return new ClusterName("Grupo " + index + ": " + core.name(), core.description());
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
