// Componente Alpine principal de DocuCanvas.
// Se registra vía el evento `alpine:init` (patrón idiomático para definir
// componentes desde módulos externos, robusto ante el orden de carga).

import * as api from './api/apiClient.js';
import { renderMap, focusChunk } from './viz/plotly3d.js';

const POLL_INTERVAL_MS = 5000;
const PLOT_ID = 'plotly-3d-map';

function createDocApp() {
    return {
        view: 'upload',
        indexingSubView: 'chunks',
        loading: false,
        question: '',
        textIngest: { title: '', content: '' },
        documents: [],
        dragOver: false,
        chatHistory: [
            { role: 'ai', content: '¡Hola! Soy tu asistente DocuCanvas. Sube un documento o selecciona uno existente para comenzar el análisis.' },
        ],

        // Visualization state
        chunksData: [],
        loadingChunks: false,
        chunksLoaded: false,
        selectedDocument: '',
        hoveredChunk: null,
        selectedChunkId: null,

        // Ask view — filtro por documento
        selectedAskDocumentId: '',

        // Indexing view state
        selectedDocId: null,
        selectedDocTitle: '',
        selectedDocSourceType: '',
        documentChunks: [],
        loadingDocChunks: false,

        // Modal state
        chunkModal: { open: false, chunk: null },

        // Polling state
        pollTimer: null,

        // Computed
        get uniqueDocuments() {
            return [...new Set(this.chunksData.map((c) => c.documentName).filter((n) => n && n !== 'Desconocido'))];
        },

        get filteredChunks() {
            const data = this.chunksData || [];
            if (!this.selectedDocument) return data;
            return data.filter((c) => c.documentName === this.selectedDocument);
        },

        get clusterGroups() {
            const counts = {};
            const firstChunks = {};
            this.filteredChunks.forEach((c) => {
                counts[c.clusterName] = (counts[c.clusterName] || 0) + 1;
                if (!firstChunks[c.clusterName]) firstChunks[c.clusterName] = c;
            });
            return Object.keys(counts).map((name) => ({
                name,
                description: firstChunks[name].clusterDescription,
                count: counts[name],
                color: firstChunks[name].clusterColor,
                preview: firstChunks[name].content.substring(0, 45).replace(/\n/g, ' '),
            })).sort((a, b) => b.count - a.count);
        },

        // Initialization
        init() {
            this.loadDocuments();
            this.schedulePoll();
        },

        // Reprograma el sondeo solo si hay documentos en proceso.
        schedulePoll() {
            if (this.pollTimer) clearTimeout(this.pollTimer);
            const hasProcessing = this.documents.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING');
            if (!hasProcessing) return;
            this.pollTimer = setTimeout(async () => {
                if (this.view === 'upload' || this.view === 'indexing') {
                    await this.loadDocuments();
                }
                this.schedulePoll();
            }, POLL_INTERVAL_MS);
        },

        async loadDocuments() {
            try {
                const data = await api.listDocuments();
                const signature = (list) => list.map((d) => `${d.id}:${d.status}:${d.chunkCount}`).join('|');
                if (signature(data) !== signature(this.documents)) {
                    this.documents = data;
                }
            } catch (e) { console.error('Error loading documents:', e); }
        },

        async uploadFile(file) {
            this.loading = true;
            try {
                await api.uploadDocument(file);
                await this.loadDocuments();
                this.schedulePoll();
            } catch (e) { console.error('Upload error:', e); }
            finally { this.loading = false; }
        },

        async ingestText() {
            if (!this.textIngest.title || !this.textIngest.content) return;
            this.loading = true;
            try {
                await api.importText({ ...this.textIngest, sourceType: 'TEXT' });
                this.textIngest = { title: '', content: '' };
                await this.loadDocuments();
                this.schedulePoll();
            } catch (e) { console.error('Ingestion error:', e); }
            finally { this.loading = false; }
        },

        async fetchDocumentChunks(docId, docTitle) {
            this.selectedDocId = docId;
            this.selectedDocTitle = docTitle;
            const doc = this.documents.find((d) => d.id === docId);
            this.selectedDocSourceType = doc ? doc.sourceType.toUpperCase() : '';
            this.loadingDocChunks = true;
            this.documentChunks = [];
            try {
                this.documentChunks = await api.getDocumentChunks(docId);
            } catch (e) { console.error('Error fetching chunks:', e); }
            finally { this.loadingDocChunks = false; }
        },

        async fetchChunks() {
            this.loadingChunks = true;
            try {
                this.chunksData = await api.getVisualizationChunks();
                this.chunksLoaded = true;
                setTimeout(() => this.render3DMap(), 100);
            } catch (e) { console.error('Viz fetch error:', e); }
            finally { this.loadingChunks = false; }
        },

        async askQuestion() {
            if (!this.question.trim()) return;
            const q = this.question;
            this.chatHistory.push({ role: 'user', content: q });
            this.question = '';
            this.loading = true;

            this.$nextTick(() => {
                const chat = document.getElementById('chatbox');
                if (chat) chat.scrollTop = chat.scrollHeight;
            });

            try {
                const askBody = { question: q, maxChunks: 5 };
                if (this.selectedAskDocumentId) askBody.documentId = this.selectedAskDocumentId;
                const data = await api.askQuestion(askBody);
                this.chatHistory.push({
                    role: 'ai',
                    content: data.answer,
                    citations: data.citations,
                    imageUrl: data.imageUrl,
                    insights: data.insights,
                    metrics: {
                        retrieval: data.retrievalTimeMs,
                        generation: data.generationTimeMs,
                        visual: data.imageTimeMs,
                    },
                });
            } catch (e) {
                this.chatHistory.push({ role: 'ai', content: 'Lo siento, hubo un error al procesar tu pregunta. Verifica la conexión con el servidor.' });
            } finally {
                this.loading = false;
                this.$nextTick(() => {
                    const chat = document.getElementById('chatbox');
                    if (chat) chat.scrollTop = chat.scrollHeight;
                });
            }
        },

        render3DMap() {
            const plotDiv = document.getElementById(PLOT_ID);
            renderMap(plotDiv, this.filteredChunks, {
                onHover: (chunk) => { this.hoveredChunk = chunk; },
                onUnhover: () => { this.hoveredChunk = null; },
                onClick: (chunk) => this.selectChunk(chunk),
            });
        },

        selectChunk(chunk) {
            this.selectedChunkId = chunk.id;
            focusChunk(document.getElementById(PLOT_ID), chunk.coordinates);
        },

        openChunkModal(chunk) {
            this.chunkModal = { open: true, chunk };
        },

        closeChunkModal() {
            this.chunkModal = { open: false, chunk: null };
        },

        handleFileUpload(event) {
            const file = event.target.files[0];
            if (file) this.uploadFile(file);
        },

        handleDrop(event) {
            this.dragOver = false;
            const file = event.dataTransfer.files[0];
            if (file) this.uploadFile(file);
        },
    };
}

document.addEventListener('alpine:init', () => {
    window.Alpine.data('docApp', createDocApp);
});
