package com.docucanvas.integration;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modelo de embeddings determinista para tests de integración.
 *
 * <p><b>Por qué no se usa Ollama aquí:</b> el test verifica el <em>cableado</em>
 * del pipeline —que el texto se trocea, se vectoriza, se persiste en PGVector y
 * se recupera por similitud—, no la calidad semántica de un modelo concreto.
 * Depender de Ollama haría que el test fallase en cualquier máquina sin los
 * modelos descargados, que es justo lo contrario de lo que debe hacer una red de
 * seguridad.
 *
 * <p>No devuelve vectores aleatorios: proyecta cada palabra a una dimensión por
 * hash y acumula frecuencias, de modo que dos textos que comparten vocabulario
 * quedan cerca en coseno. Eso permite que las aserciones sobre <em>qué</em>
 * chunk se recupera sean significativas y no puro azar.
 */
public class DeterministicEmbeddingModel implements EmbeddingModel {

    /** Debe coincidir con la dimensión declarada en la configuración de PGVector. */
    public static final int DIMENSIONS = 768;

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            vector[0] = 1.0f; // vector no nulo: pgvector rechaza el vector cero en coseno
            return vector;
        }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+")) {
            if (word.length() < 3) continue;
            int dim = Math.floorMod(word.hashCode(), DIMENSIONS);
            vector[dim] += 1.0f;
        }
        return normalize(vector);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /** Norma L2: la similitud coseno de pgvector compara dirección, no magnitud. */
    private float[] normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }
        for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
        return vector;
    }
}
