# What-If: `what-if/rag-multimodal`

> Versión experimental de DocuCanvas. La rama base queda intacta; aquí se aplican
> las mejoras de arquitectura, RAG, Spring AI y multimodalidad de la auditoría.

Este documento explica **qué cambió y por qué**, para poder comparar BASE vs
WHAT-IF y decidir después qué merece incorporarse.

---

## El problema que motivó la rama

La auditoría encontró una brecha entre lo que el proyecto anunciaba y lo que el
código hacía:

| Se anunciaba | Hacía realmente (rama base) |
| :--- | :--- |
| Respuesta generada con **Gemini** | Ollama local (`llama3.2`). Cero dependencias de Gemini en el `pom.xml` |
| Imagen generada con **`ImageModel` de Spring AI** | Cero imports de `org.springframework.ai.image.*`. Se elegía una de 8 plantillas SVG escritas a mano según coincidencia contra listas de palabras clave |
| **El LLM deriva un prompt visual** del contexto | La imagen se generaba en un hilo paralelo e independiente, a partir del contexto crudo y de frecuencia de palabras — sin ver nunca la respuesta del modelo |

Ninguna de esas tres cosas es un defecto de implementación: son piezas que no
existían. Esta rama las construye.

---

## Cambios por área

### Arquitectura

- **`QuestionService` deja de ser un God Service.** Tenía 385 líneas y cuatro
  responsabilidades que cambian por razones distintas: búsqueda vectorial,
  construcción de prompts, concurrencia/timeouts, parseo de salida y cálculo de
  insights. La recuperación vive ahora en `RagRetriever`, los prompts en
  `RagPromptFactory` y la generación estructurada en `StructuredInsightGenerator`.
- **Nuevo paquete `application/visual`** con el pipeline multimodal como pieza de
  primer nivel, no como efecto secundario del pipeline de preguntas.
- **Dos puertos nuevos**: `GenerativeImagePort` (generación por IA, puede no haber
  proveedor) y `DiagramRenderPort` (render local, siempre disponible).

### RAG

- **Estrategia de dos umbrales con nombre.** Antes eran los literales `0.3` y `0.0`
  dentro de un método; ahora son `strictThreshold` y `relaxedThreshold`,
  configurables. Cuando hay que relajar el umbral, se declara en el panel de
  transparencia en vez de quedarse en un log: la evidencia recuperada es la más
  cercana disponible, no necesariamente una coincidencia fuerte, y el usuario
  merece saberlo.
- **`topK` acotado** por `@Max(20)` en el DTO y recortado también en el servicio.
- **Parámetros de chunking externalizados** a `docucanvas.rag.chunking`.

### Spring AI

- **Salida estructurada** (`BeanOutputConverter` + record `RagInsight`) en lugar de
  pedir un bloque de texto `[RELACIONES]` y extraerlo con expresiones regulares.
- **`ChatOptions` portable** en lugar de `OllamaOptions`. Es lo que permite cambiar
  de proveedor sin tocar lógica de negocio.
- **`ImageModel`** usado de verdad, detrás de un puerto y con degradación.
- **`PromptTemplate`** en lugar de concatenar `String`.

### Multimodalidad — el "plot twist"

El flujo pasa de dos caminos paralelos e independientes a una cadena real:

```
Documento → Chunking → Embeddings → PGVector
                                        ↓
Pregunta → RagRetriever (top-K + umbral) → Contexto
                                        ↓
            ChatClient + BeanOutputConverter → RagInsight { answer, visualPrompt, relations }
                                        ↓
                     visualPrompt → ImageModel → imagen
                                        ↓ (fallo / sin proveedor)
                                   Diagrama SVG local
```

El prompt visual sale de la **misma llamada** y del **mismo contexto** que la
respuesta textual, con reglas explícitas en el system prompt que prohíben
introducir elementos que no aparezcan en el contexto: la misma mitigación de
alucinaciones que ancla el texto, aplicada a la segunda modalidad.

### Seguridad y validaciones

- **Tipo real de archivo por magic bytes** (`FileTypeDetectorPort` + Tika `detect()`).
  Antes bastaba renombrar `payload.exe` a `payload.pdf` para que los bytes llegaran
  intactos a PDFBox.
- **Cotas de entrada**: `maxChunks` ≤ 20, `question` ≤ 2000 caracteres,
  `content` ≤ 1.000.000. El rate limiting acota la frecuencia de las peticiones, no
  el tamaño de cada una.

### Un bug encontrado al levantar el entorno

El healthcheck de Ollama en `docker-compose.yml` usaba
`curl -sf http://localhost:11434/api/tags`, pero **la imagen `ollama/ollama` no
incluye `curl`**: el chequeo fallaba siempre con `curl: not found`. El contenedor
quedaba marcado `unhealthy` de forma permanente aunque su API funcionara, y como
`ollama-init` depende de `service_healthy`, **los modelos no se descargaban nunca**.

En una máquina limpia, el arranque en 3 pasos del README fallaba en silencio: el
contenedor arriba, la API respondiendo, y ningún modelo cargado. Es exactamente el
tipo de fallo que aparece al preparar una demo en una máquina prestada.

Corregido usando el CLI propio de la imagen (`ollama list`). Verificado: el
contenedor pasa a `healthy` en ~20s y `ollama-init` descarga los modelos.

### Un bug encontrado por los tests

`SpringAiImageModelAdapter` se registró primero con
`@ConditionalOnBean(ImageModel.class)`. Es incorrecto: esa condición solo es fiable
en clases de autoconfiguración, porque se evalúa en el orden de registro de las
definiciones de bean, y un `@Component` escaneado se registra **antes** que las
autoconfiguraciones que aportan el `ImageModel`. El adaptador no se habría
registrado nunca, ni con el starter presente — y el síntoma habría sido engañoso:
la aplicación arranca bien y degrada siempre al SVG. Se corrigió con
`ObjectProvider`, que resuelve la dependencia de forma perezosa.

---

## Cómo ejecutarlo

### Por defecto (sin credenciales externas)

```bash
docker-compose up -d
./mvnw spring-boot:run
```

Chat con Ollama local, imagen con el diagrama SVG local. El campo `imageSource`
de la respuesta dirá `LOCAL_SVG_FALLBACK`.

### Con Gemini

Requiere perfil de Maven (aporta el starter) **y** perfil de Spring (aporta la
configuración):

```bash
./mvnw spring-boot:run -Pgemini -Dspring-boot.run.profiles=dev,gemini
```

Variables de entorno necesarias — **no hay ninguna credencial en el repositorio**:

| Variable | Para qué |
| :--- | :--- |
| `GOOGLE_CLOUD_PROJECT` | ID del proyecto de Google Cloud |
| `GOOGLE_CLOUD_LOCATION` | Región (por defecto `us-central1`) |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta al JSON de la service account, o autenticarse con `gcloud auth application-default login` |

Los **embeddings siguen en Ollama a propósito**: cambiarlos invalidaría todos los
vectores ya indexados, porque un embedding de `nomic-embed-text` (768 dimensiones)
y uno de Vertex AI no viven en el mismo espacio vectorial. Migrar de modelo de
embeddings obliga a reindexar el corpus entero y a cambiar la dimensión de la
columna en PGVector.

### Con generación real de imágenes

```bash
./mvnw spring-boot:run -Pimagegen -Dspring-boot.run.profiles=dev,imagegen
```

Requiere `OPENAI_API_KEY`. Con esto, `imageSource` pasa a `GENERATIVE_IMAGE_MODEL`.

Spring AI 1.0.0 GA todavía no trae un `ImageModel` para Imagen de Vertex AI; cuando
lo traiga, basta cambiar el starter del perfil: `SpringAiImageModelAdapter` no se toca.

### Ambos a la vez

```bash
./mvnw spring-boot:run -Pgemini,imagegen -Dspring-boot.run.profiles=dev,gemini,imagegen
```

---

## Decisiones que se apartaron de la recomendación original

**No se adoptó `QuestionAnswerAdvisor`.** La auditoría lo recomendaba, pero al
implementarlo no encajaba, por dos razones concretas:

1. El Advisor ejecuta **una única búsqueda**, así que la estrategia de dos umbrales
   no es expresable dentro de él. Sin ella, las preguntas abstractas o de síntesis
   se quedan sin ninguna evidencia.
2. La UI necesita igualmente los documentos recuperados **con su score** para las
   citaciones y el panel de confianza. Cuando de todas formas hay que tener los
   documentos en la mano, el Advisor no ahorra trabajo: solo esconde dónde ocurre.

La decisión está documentada en el javadoc de `RagRetriever`. Para la charla es
mejor material que usar el Advisor por defecto: es un ejemplo real de **cuándo no
usar la abstracción del framework**, que es una conversación más interesante que
"usa siempre lo que trae la caja".

---

## Estado de verificación

| | |
| :--- | :--- |
| Compilación por defecto | ✅ |
| Compilación con `-Pgemini,imagegen` | ✅ |
| Tests | ✅ **73/73** (la rama base tenía 53) |
| Integración con PGVector real (Testcontainers) | ✅ `DocuCanvasApplicationTests` |
| Arranque de la aplicación | ✅ Flyway valida 8 migraciones, Tomcat en 8080, `Started DocuCanvasApplication in 29.9s` |
| Contexto de Spring sin base de datos | ✅ `ApplicationWiringTest` |
| Credenciales en el repositorio | ✅ ninguna |

### Prueba de extremo a extremo con modelos reales

Ejecutada contra Ollama (`llama3.2` + `nomic-embed-text`) y PGVector: ingesta de
un documento → embeddings → búsqueda semántica → generación estructurada → render
visual.

**La salida estructurada funciona con un modelo de 3B: 4 de 4 consultas
devolvieron JSON válido.** Era el riesgo principal del diseño. Ejemplo real:

```
answer:       "El servicio de conciliacion consume eventos de transacciones de la
               cola de mensajes Kafka y contrasta cada movimiento con el extracto
               del banco cada 24 horas..."
visualPrompt: "A service consuming events from a Kafka message queue and comparing
               each transaction with the bank statement every 24 hours, generating
               an alert if a discrepancy is detected."
relations:    [{"concept":"Servicio de conciliacion",
                "relatedTopics":["cola de mensajes","eventos de transacciones",
                                 "extracto del banco"]}]
imageSource:  LOCAL_SVG_FALLBACK      (sin -Pimagegen no hay proveedor de imagen)
confianza:    ALTA (0.671)
```

El `visualPrompt` describe únicamente entidades presentes en el documento —Kafka,
extracto bancario, ciclo de 24 h, alerta— y no introduce elementos inventados: la
cadena multimodal hace lo que promete.

**Dos observaciones para la charla:**

1. **Latencia.** La generación tardó entre 28 y 80 segundos por consulta
   (llama3.2 en CPU). El timeout dinámico calculó bien y ninguna consulta expiró,
   pero 80 segundos de silencio en un escenario son inviables. Es el argumento
   más fuerte para usar Gemini en la demo, o para tener respuestas pre-cacheadas.
2. **El modelo no siempre sigue la regla 7** del system prompt (pedir
   explícitamente que la imagen no contenga texto). Solo importa cuando hay un
   proveedor de imagen real conectado; conviene reforzarlo en el propio adaptador
   añadiendo el sufijo al prompt antes de llamar al `ImageModel`.
