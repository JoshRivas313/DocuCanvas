-- V7__Extract_File_Content_To_Blob_Table.sql
-- El binario original (hasta 10MB) vivía como BYTEA en la tabla `documents`,
-- hinchando la fila principal en cada listado/actualización de estado y
-- encareciendo los backups. Se extrae a una tabla dedicada, cargada solo
-- cuando se solicita explícitamente el archivo (GET /documents/{id}/file).

CREATE TABLE IF NOT EXISTS document_blobs (
    document_id UUID PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    content BYTEA NOT NULL
);

INSERT INTO document_blobs (document_id, content)
SELECT id, file_content FROM documents WHERE file_content IS NOT NULL;

ALTER TABLE documents DROP COLUMN file_content;
