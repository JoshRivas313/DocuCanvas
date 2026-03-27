-- Hace que chunk_index permita Null temporalmente si spring-ai no está mapeando correctamente esa columna.
-- Fallback para hacer la demo resiliente si el vector store omite la data.
ALTER TABLE document_chunks ALTER COLUMN chunk_index DROP NOT NULL;
