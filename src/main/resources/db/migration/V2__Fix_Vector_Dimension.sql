-- V2__Fix_Vector_Dimension.sql
-- Adjusting vector dimension to 768 to match Google Gemini text-embedding-004
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE VECTOR(768);
