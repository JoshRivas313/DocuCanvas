package com.docucanvas.application.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extrae conceptos clave y oraciones representativas de un texto mediante
 * frecuencia de palabras. Heurística simple y determinista (sin LLM): se usa
 * tanto para la infografía del pipeline RAG como para la sección "Conceptos
 * principales" del informe, garantizando que ambas muestren exactamente los
 * mismos conceptos.
 */
@Component
public class ConceptExtractor {

    // Palabras vacías en español e inglés que no aportan contexto visual
    private static final Set<String> STOP_WORDS = Set.of(
            "de", "la", "el", "en", "y", "a", "los", "las", "un", "una",
            "es", "se", "que", "por", "con", "no", "al", "del", "su", "lo",
            "the", "an", "in", "is", "it", "of", "to", "and", "or",
            "for", "on", "are", "at", "be", "this", "that", "with", "from",
            "was", "has", "had", "have", "not", "but", "they", "we", "you",
            "si", "como", "más", "pero", "sus", "le", "ya", "o", "este",
            "también", "hasta", "hay", "donde", "han", "quien", "siendo"
    );

    /** Devuelve las palabras más frecuentes del texto, descartando palabras vacías. */
    public List<String> extractKeyConcepts(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[\\s\\p{Punct}]+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() > 4)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> capitalize(e.getKey()))
                .collect(Collectors.toList());
    }

    /** Extrae oraciones breves y representativas del texto (30–120 caracteres). */
    public List<String> extractKeySentences(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[.!?\\n]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 30 && s.length() <= 120)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
