package com.docucanvas.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConceptExtractor — Conceptos y oraciones clave por frecuencia")
class ConceptExtractorTest {

    private final ConceptExtractor extractor = new ConceptExtractor();

    @Test
    @DisplayName("Debe devolver las palabras más frecuentes, descartando palabras vacías")
    void devuelvePalabrasMasFrecuentes() {
        String text = "blockchain blockchain blockchain transacciones transacciones el de la";

        List<String> concepts = extractor.extractKeyConcepts(text, 3);

        assertThat(concepts).hasSize(2);
        assertThat(concepts.get(0)).isEqualTo("Blockchain");
        assertThat(concepts.get(1)).isEqualTo("Transacciones");
    }

    @Test
    @DisplayName("Palabras de 4 caracteres o menos se descartan (ruido, no conceptos)")
    void descartaPalabrasCortas() {
        List<String> concepts = extractor.extractKeyConcepts("gato gato gato perro perro casa", 5);

        assertThat(concepts).doesNotContain("Gato", "Casa"); // "gato"=4 chars, "casa"=4 chars: descartados
    }

    @Test
    @DisplayName("Texto vacío o nulo devuelve lista vacía, no null ni excepción")
    void textoVacioDevuelveListaVacia() {
        assertThat(extractor.extractKeyConcepts(null, 5)).isEmpty();
        assertThat(extractor.extractKeyConcepts("", 5)).isEmpty();
        assertThat(extractor.extractKeySentences(null, 3)).isEmpty();
    }

    @Test
    @DisplayName("Extrae oraciones dentro del rango de longitud esperado (30-120 caracteres)")
    void extraeOracionesEnRangoDeLongitud() {
        String text = "Corta. " +
                "Esta es una oración de longitud razonable para ser representativa del contenido. " +
                "Esta otra oración es demasiado larga y por lo tanto debería quedar excluida del resultado final porque supera el límite de ciento veinte caracteres establecido.";

        List<String> sentences = extractor.extractKeySentences(text, 5);

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0)).contains("longitud razonable");
    }
}
