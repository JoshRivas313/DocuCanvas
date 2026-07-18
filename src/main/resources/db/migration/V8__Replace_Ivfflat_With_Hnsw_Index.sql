-- V8__Replace_Ivfflat_With_Hnsw_Index.sql
--
-- CAUSA RAÍZ del fallo de recuperación RAG: el índice IVFFlat creado en V6
-- usaba `lists = 100` sin relación al volumen real de datos. Con pocos miles
-- de filas (o menos, como en este caso: ~190), cada una de las 100 listas
-- contiene en promedio menos de 2 vectores. Con `ivfflat.probes = 1` (valor
-- por defecto), una búsqueda por similitud solo visita 1 de esas 100
-- particiones, ignorando ~99% del dataset — incluyendo, en la práctica,
-- prácticamente todos los chunks realmente relevantes.
--
-- Verificado con SQL directo: un seq scan (bypass del índice) encuentra
-- correctamente los chunks del documento correcto con similitud 0.58-0.62;
-- la misma consulta usando el índice IVFFlat devolvía un único resultado
-- irrelevante (similitud 0.51) de otro documento.
--
-- HNSW no sufre este problema: no particiona el espacio según un parámetro
-- que dependa del volumen de filas, ofrece mejor recall en datasets
-- pequeños/medianos y es el índice recomendado por pgvector desde v0.5.0
-- para la mayoría de los casos (además, es el tipo de índice por defecto
-- que Spring AI 1.0 GA crea cuando gestiona el esquema él mismo — aquí lo
-- gestionamos con Flyway, así que había quedado desalineado).

DROP INDEX IF EXISTS document_chunks_embedding_idx;

CREATE INDEX document_chunks_embedding_idx
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
