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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Nombra clusters semánticamente usando el LLM (Ollama) a partir de una muestra
 * de sus contenidos. Adaptador de infraestructura que implementa
 * {@link ClusterNamingPort}. Si el LLM falla o su respuesta es inutilizable,
 * degrada a una heurística local por cluster.
 *
 * <p><b>Nombrado por lote (una sola llamada al LLM):</b> nombrar k clusters
 * con k llamadas independientes multiplica la latencia por k. Con Ollama en
 * CPU, además, la "paralelización" del lado del cliente no ayuda si el
 * servidor de inferencia solo procesa una petición a la vez — el tiempo
 * total termina siendo la suma de las k latencias, no el máximo. Este
 * adaptador arma un único prompt con la muestra de cada cluster PENDIENTE
 * (los que no están en caché) y le pide al modelo que devuelva los k
 * resultados en un solo turno, reduciendo el peor caso de {@code k} llamadas
 * a 1.
 *
 * <p><b>Caché por firma de contenido:</b> el resultado se cachea con la
 * propia muestra como clave, independiente del índice de cluster — así,
 * entre visualizaciones (vista global vs. por documento, o tras una nueva
 * ingesta que invalida solo la caché de vista), un cluster cuyo contenido no
 * cambió reutiliza su nombre sin volver a llamar al LLM. Los resultados del
 * fallback heurístico NO se cachean, para permitir reintento en la
 * siguiente visualización.
 */
@Component
public class LlmClusterNamingAdapter implements ClusterNamingPort {

    private static final Logger log = LoggerFactory.getLogger(LlmClusterNamingAdapter.class);
    // Con hasta MAX_CLUSTERS clusters en una sola llamada por lote, el tamaño
    // de la muestra impacta directamente el tiempo de "prefill" del LLM
    // (procesar el prompt de entrada antes de generar). 2 fragmentos de 150
    // caracteres siguen siendo suficientes para clasificar el tema de un
    // cluster, y reducen el prompt total a la mitad frente a 3×200.
    private static final int SAMPLE_CHUNKS = 2;
    private static final int SAMPLE_CHARS = 150;
    private static final Pattern CLUSTER_BLOCK = Pattern.compile(
            "Cluster\\s+(\\d+)\\s*:\\s*Nombre:\\s*(.*?)\\s*Descripción:\\s*(.*?)(?=Cluster\\s+\\d+\\s*:|\\z)",
            Pattern.DOTALL);

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
    public Map<Integer, ClusterName> nameAll(Map<Integer, List<String>> clusterContentsByIndex) {
        Map<Integer, ClusterName> result = new HashMap<>();
        Map<Integer, String> samples = new HashMap<>();
        Map<Integer, NameCore> resolved = new HashMap<>();

        for (Map.Entry<Integer, List<String>> entry : clusterContentsByIndex.entrySet()) {
            int index = entry.getKey();
            List<String> contents = entry.getValue();
            if (contents.isEmpty()) {
                // Caso especial: no hay prefijo "Grupo N:" que anteponer, el
                // nombre completo ya es el resultado final.
                result.put(index, new ClusterName("Grupo " + index, "Sin contenido"));
                continue;
            }
            String sample = buildSample(contents);
            NameCore cached = nameCache.getIfPresent(sample);
            if (cached != null) {
                resolved.put(index, cached);
            } else {
                samples.put(index, sample);
            }
        }

        if (!samples.isEmpty()) {
            Map<Integer, NameCore> fromLlm = askLlmBatch(samples);
            for (Map.Entry<Integer, String> entry : samples.entrySet()) {
                int index = entry.getKey();
                NameCore core = fromLlm.get(index);
                if (core == null) {
                    core = fallback(clusterContentsByIndex.get(index));
                } else {
                    nameCache.put(entry.getValue(), core);
                }
                resolved.put(index, core);
            }
        }

        resolved.forEach((index, core) -> result.put(index, compose(index, core)));
        return result;
    }

    private String buildSample(List<String> contents) {
        return contents.stream()
                .limit(SAMPLE_CHUNKS)
                .map(s -> s.substring(0, Math.min(s.length(), SAMPLE_CHARS)))
                .collect(Collectors.joining("\n--- \n"));
    }

    /**
     * Pide al LLM el nombre+descripción de todos los clusters pendientes en
     * un único turno. Devuelve solo las entradas que pudo parsear con éxito;
     * las faltantes se resuelven con el fallback heurístico en el llamador.
     */
    private Map<Integer, NameCore> askLlmBatch(Map<Integer, String> samplesByIndex) {
        try {
            String userPrompt = samplesByIndex.entrySet().stream()
                    .map(e -> "Cluster " + e.getKey() + ":\n" + e.getValue())
                    .collect(Collectors.joining("\n\n"));

            String response = chatClient.prompt()
                    .system("Eres un experto en taxonomía. A continuación se listan varios clusters de "
                            + "fragmentos de texto, cada uno identificado como 'Cluster N:'. Para CADA UNO, "
                            + "responde exactamente en este formato, uno tras otro, sin texto adicional:\n"
                            + "Cluster N:\n"
                            + "Nombre: [1-2 palabras]\n"
                            + "Descripción: [una oración breve y profesional sobre el contenido]")
                    .user(userPrompt)
                    .call()
                    .content();

            return parseBatchResponse(response);
        } catch (Exception e) {
            log.warn("Fallo en la llamada de nombrado por lote a la IA, se usará fallback local", e);
            return Map.of();
        }
    }

    private Map<Integer, NameCore> parseBatchResponse(String response) {
        Map<Integer, NameCore> parsed = new HashMap<>();
        Matcher matcher = CLUSTER_BLOCK.matcher(response);
        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1));
                String name = matcher.group(2).replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ\\s]", "").trim();
                String description = matcher.group(3).trim();
                if (!name.isEmpty()) {
                    String capitalized = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
                    parsed.put(index, new NameCore(capitalized, description));
                }
            } catch (NumberFormatException ignored) {
                // bloque malformado: se resuelve con fallback en el llamador
            }
        }
        return parsed;
    }

    private ClusterName compose(int index, NameCore core) {
        return new ClusterName("Grupo " + index + ": " + core.name(), core.description());
    }

    private NameCore fallback(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return new NameCore("Grupo", "Sin contenido");
        }
        String longestWord = Arrays.stream(contents.get(0).split("\\s+"))
                .map(s -> s.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ]", ""))
                .filter(s -> s.length() > 4)
                .max(Comparator.comparingInt(String::length))
                .orElse("Tema");

        String capitalized = longestWord.substring(0, 1).toUpperCase() + longestWord.substring(1).toLowerCase();
        return new NameCore(capitalized, "Fragmentos relacionados con " + capitalized.toLowerCase() + ".");
    }
}
