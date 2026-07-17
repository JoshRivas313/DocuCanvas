# DocuCanvas 🎨 (Visual RAG Edition)

![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M5-green)
![RAG](https://img.shields.io/badge/Architecture-Hexagonal-blue)
![ImageModel](https://img.shields.io/badge/AI-ImageModel-magenta)
![Status](https://img.shields.io/badge/Status-Demo--Ready-emerald)

> **"No leas la documentación, haz que Spring AI te la dibuje"**

DocuCanvas es una plataforma de consulta inteligente sobre documentos que utiliza la potencia de **Spring AI** para no solo responder preguntas basadas en contexto real (RAG), sino también para **generar una representación visual** de ese conocimiento de forma dinámica mediante un mapa 3D semántico.

---

## 🛠️ Stack Tecnológico

| Tecnología | Versión | Por qué se usa |
| :--- | :--- | :--- |
| **Java** | 21 | Soporte para Virtual Threads y características modernas del lenguaje. |
| **Spring Boot** | 3.4.4 | Framework base robusto con soporte nativo para IA. |
| **Spring AI** | 1.0.0-M5 | Abstracciones para VectorStore y ChatClient. |
| **Ollama** | Latest | LLM local (`llama3.2`) para RAG y embeddings (`nomic-embed-text`, 768d). 100% local, sin API externa. |
| **PGVector** | 16 | Extensión de PostgreSQL para búsqueda vectorial nativa con SQL. |
| **Thymeleaf** | 3.x | Motor de plantillas para un frontend modular y eficiente. |
| **Alpine.js** | 3.x | Reactividad ligera para la gestión de estados en la UI. |
| **Plotly.js** | 2.32.0 | Visualización 3D interactiva de los vectores de embedding. |
| **Apache Commons Math** | 3.6.1 | Optimización de PCA mediante **SVD (Singular Value Decomposition)**. |

---

## 🚀 Ejecución Rápida (3 Pasos)

1. **Iniciar Infraestructura**: Levanta PGVector en el puerto 5433.
   ```powershell
   docker-compose up -d
   ```
2. **Arrancar Aplicación**: Inicia el backend y frontend integrado.
   ```powershell
   ./mvnw spring-boot:run
   ```
3. **Acceso Web**: Abre tu navegador en:
   > [http://localhost:8080](http://localhost:8080)

---

## 📂 Estructura del Proyecto

```text
c:/intelijent/Proyecto_Base_SpringBoot/
├── src/main/java/com/docucanvas/
│   ├── api/controller/             # Controladores REST y Web (Thymeleaf)
│   ├── application/service/        # Lógica Central: Ingesta, PCA (SVD), Clustering (KMeans)
│   ├── application/usecase/        # Casos de uso de negocio (UploadDocument)
│   ├── domain/model/               # Entidades de dominio (Document, Chunk)
│   └── infrastructure/             # Adaptadores de Persistence (PGVector), AI y Config
├── src/main/resources/
│   ├── db/migration/               # Flyway SQL (Evolución del Schema)
│   ├── templates/                  # Template Principal (index.html)
│   ├── templates/fragments/        # Fragmentos modulares (upload, indexing, ask, chunks)
│   └── application.yaml            # Configuración OpenAI, PGVector y DB
├── docker-compose.yml              # Servidor PGVector (puerto 5433)
└── pom.xml                         # Dependencias Maven y Spring Boot
```

---

## 🔌 Referencia de la API

### 📄 Gestión de Documentos

#### 1. Listar Documentos
- **URL**: `GET /api/v1/documents`
- **Uso**: Obtiene todos los documentos indexados en el sistema.
- **Response**:
```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "title": "Manual_Gatos.pdf",
    "sourceType": "PDF",
    "status": "READY",
    "chunkCount": 12
  }
]
```

#### 2. Subir Archivo (Multipart)
- **URL**: `POST /api/v1/documents/upload`
- **Uso**: Sube un archivo físico (PDF, DOCX, TXT) para su procesamiento.
- **Request (Form-Data)**:
  - `file`: [Binario del archivo]
  - `title`: "Nombre del Documento" (Opcional)
- **Response**:
```json
{
  "id": "uuid-generado",
  "title": "Nombre del Documento",
  "status": "READY"
}
```

#### 3. Ver/Descargar Archivo Binarizado
- **URL**: `GET /api/v1/documents/{id}/file`
- **Uso**: Recupera el contenido binario original para previsualización.
- **Response**: `Flujo de bytes con el Content-Type correcto (inline).`

---

### 📊 Visualización y Semántica

#### 4. Visualizar Chunks (3D)
- **URL**: `GET /api/v1/chunks/visualize`
- **Uso**: Obtiene todos los fragmentos con sus coordenadas 3D (PCA/SVD) y cluster asignado.
- **Response**:
```json
[
  {
    "id": "chunk-uuid",
    "content": "Contenido del fragmento...",
    "coordinates": [0.45, -0.12, 0.89],
    "cluster": 1,
    "documentName": "Manual.pdf"
  }
]
```

---

### 🧠 Inteligencia Artificial (RAG)

#### 5. Pregunta RAG + Infografía
- **URL**: `POST /api/v1/questions/ask`
- **Uso**: Responde con el contexto de los documentos (Ollama) y genera una infografía SVG local en paralelo.
- **Request (JSON)**:
```json
{
  "question": "¿De qué trata el capítulo 1?",
  "documentId": "uuid-del-doc",
  "maxChunks": 5
}
```
- **Response (JSON)**: el `imageUrl` es un Data URL SVG (`data:image/svg+xml;base64,...`), no una URL externa.
```json
{
  "question": "¿De qué trata el capítulo 1?",
  "answer": "El capítulo 1 trata sobre...",
  "citations": [{ "source": "Manual_Gatos.pdf", "content": "...", "score": 0.82 }],
  "imageUrl": "data:image/svg+xml;base64,PHN2Zy4uLg==",
  "retrievalTimeMs": 45,
  "generationTimeMs": 1200
}
```

---

## 🧠 Arquitectura de la Demo

El proyecto sigue una **Arquitectura Hexagonal**, asegurando que el dominio esté protegido de dependencias externas.

### Flujos Principales:

1. **Ingesta e Indexación**:
   - Extrae texto con **Apache Tika**.
   - Genera fragmentos inteligentes (Chunks) con **TokenTextSplitter**.
   - Calcula embeddings de **768 dimensiones** (`nomic-embed-text`) y los persiste físicamente en **PGVector**.
   - **Optimización**: Los fragmentos se proyectan a 3D usando **SVD** para una visualización fluida.

2. **RAG + Infografía**:
   - Búsqueda semántica sobre los fragmentos indexados.
   - Generación de respuesta contextual con **Ollama (`llama3.2`)**, 100% local.
   - Creación de una **infografía SVG** generada localmente (sin API externa, sin GPU) a partir del contexto recuperado.

---

## 🖼️ Módulos de la Interfaz

1.  **📤 Subir**: Carga de archivos (**PDF, DOCX, TXT**) y texto directo con persistencia binaria inmediata.
2.  **📑 Vista Indexación**: Auditoría de fragmentos y **Previsualizador Multiformato** (ver PDF/TXT al lado de los chunks).
3.  **🌍 Visor de Embeddings**: Mapa 3D interactivo con **Clusters Semánticos dinámicos** (grupos por colores).
4.  **💬 Preguntar**: Chat RAG con fuentes citadas e infografía SVG generada localmente por respuesta.

---

## 🔐 Notas de Configuración

### Perfiles (`dev` / `prod`)
El perfil activo por defecto es `dev` (acceso abierto, pensado para la demo local). Actívalo explícitamente con `SPRING_PROFILES_ACTIVE`:
```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"; ./mvnw spring-boot:run
```
- **`dev`**: acceso abierto, logging verboso del proyecto.
- **`prod`**: cabeceras de seguridad endurecidas (HSTS, Referrer-Policy), logging conservador. Punto de extensión listo para exigir autenticación (ver `SecurityConfig`).

### Credenciales (nunca hardcodeadas)
La conexión a BD se externaliza por variables de entorno (con defaults para desarrollo):
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

### Ollama (IA local)
Se configura vía `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL`. No requiere ninguna API key externa.

### Rate limiting
Los endpoints costosos (`/questions/ask`, `/documents/upload`, `/documents/import-text`) están protegidos por un límite por IP configurable: `RATELIMIT_CAPACITY` (def. 20) por `RATELIMIT_WINDOW_SECONDS` (def. 60).

### Otros
- **Database**: PostgreSQL corre en puerto `5433` vía Docker para evitar conflictos locales.
- **X-Frame-Options**: Configurado como `SAMEORIGIN` en `SecurityConfig` para permitir la previsualización de archivos en iframes.
