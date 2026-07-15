-- V6__Update_Vector_To_768_Ollama.sql
-- Ajuste de dimensión vectorial para el modelo nomic-embed-text de Ollama.
-- nomic-embed-text genera embeddings de 768 dimensiones (frente a los 1536 de OpenAI).
-- IMPORTANTE: esta migración limpia los embeddings existentes porque son incompatibles
-- entre modelos distintos de embedding. Los documentos deberán re-ingestarse.

-- 1. Eliminar datos de embeddings previos (incompatibles con el nuevo modelo)
DELETE FROM document_chunks;

-- 2. Cambiar la dimensión del vector a 768
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE VECTOR(768);

-- 3. Recrear el índice de búsqueda por similitud para la nueva dimensión
DROP INDEX IF EXISTS document_chunks_embedding_idx;
CREATE INDEX document_chunks_embedding_idx
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
