// Cliente de la API REST de DocuCanvas.
// Cada función encapsula un endpoint y devuelve datos ya parseados (o lanza).

const BASE = '/api/v1';

async function toJsonOrThrow(res) {
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
}

export async function listDocuments() {
    return toJsonOrThrow(await fetch(`${BASE}/documents`));
}

export async function uploadDocument(file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', file.name);
    return toJsonOrThrow(await fetch(`${BASE}/documents/upload`, { method: 'POST', body: formData }));
}

export async function importText({ title, content, sourceType = 'TEXT' }) {
    return toJsonOrThrow(await fetch(`${BASE}/documents/import-text`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, content, sourceType }),
    }));
}

export async function getDocumentChunks(docId) {
    return toJsonOrThrow(await fetch(`${BASE}/chunks/document/${docId}`));
}

export async function getVisualizationChunks() {
    return toJsonOrThrow(await fetch(`${BASE}/chunks/visualize`));
}

export async function askQuestion(body) {
    return toJsonOrThrow(await fetch(`${BASE}/questions/ask`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    }));
}
