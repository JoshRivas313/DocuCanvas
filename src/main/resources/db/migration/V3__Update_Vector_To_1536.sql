-- V3__Update_Vector_To_1536.sql
-- Adjusting vector dimension back to 1536 to match OpenAI text-embedding-3-small
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE VECTOR(1536);
