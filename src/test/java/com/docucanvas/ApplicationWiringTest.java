package com.docucanvas;

import com.docucanvas.application.service.QuestionService;
import com.docucanvas.application.visual.VisualRenderingService;
import com.docucanvas.infrastructure.config.RagProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el contexto completo de la aplicación se construye: todos los
 * beans del pipeline RAG y del pipeline visual se resuelven, y la configuración
 * enlaza.
 *
 * <p><b>Por qué existe además de {@code DocuCanvasApplicationTests}:</b> aquel
 * levanta la aplicación real contra PostgreSQL vía Testcontainers, así que
 * necesita Docker y no se puede ejecutar en cualquier máquina. Este verifica lo
 * que suele romperse de verdad al refactorizar — una dependencia que no se
 * resuelve, una propiedad mal escrita, un puerto sin implementación — excluyendo
 * la capa de persistencia y simulando los modelos externos. Es la red de
 * seguridad que se ejecuta siempre, no solo cuando hay Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration,"
                + "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@DisplayName("Cableado de la aplicación — el contexto se construye sin base de datos")
class ApplicationWiringTest {

    // Infraestructura externa simulada: lo que se prueba es el cableado propio,
    // no la disponibilidad de PostgreSQL ni de un modelo de IA levantado.
    @MockitoBean private VectorStore vectorStore;
    @MockitoBean private ChatModel chatModel;
    @MockitoBean private EmbeddingModel embeddingModel;
    @MockitoBean private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;
    @MockitoBean private com.docucanvas.domain.repository.DocumentRepository documentRepository;
    @MockitoBean private com.docucanvas.application.port.out.BlobStoragePort blobStoragePort;

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("Los servicios del pipeline RAG y visual se resuelven")
    void elPipelineCompletoSeResuelve() {
        assertThat(context.getBean(QuestionService.class)).isNotNull();
        assertThat(context.getBean(VisualRenderingService.class)).isNotNull();
        assertThat(context.getBean(com.docucanvas.application.rag.RagRetriever.class)).isNotNull();
        assertThat(context.getBean(com.docucanvas.application.rag.StructuredInsightGenerator.class)).isNotNull();
    }

    @Test
    @DisplayName("Sin proveedor de imágenes configurado, el respaldo local está disponible")
    void elRespaldoVisualEstaSiempreDisponible() {
        var generative = context.getBean(com.docucanvas.application.port.out.GenerativeImagePort.class);
        var diagram = context.getBean(com.docucanvas.application.port.out.DiagramRenderPort.class);

        assertThat(diagram).isNotNull();
        // El build por defecto no incluye ningún starter de imagen.
        assertThat(generative.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("La configuración de RAG se enlaza desde application.yaml")
    void laConfiguracionSeEnlaza() {
        RagProperties props = context.getBean(RagProperties.class);
        assertThat(props.chunking().chunkSize()).isPositive();
        assertThat(props.retrieval().maxTopK()).isPositive();
        assertThat(props.generation().model()).isNotBlank();
    }
}
