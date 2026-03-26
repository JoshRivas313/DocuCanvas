# Plan for Analyzing DocuCanvas

- [x] Review project architecture for RAG implementation
 - [x] Update pom.xml and application.yaml configuration.
 - [x] Integrate `ImageModel` directly within `ImageGenerationService` or `QuestionService`.
 - [x] Update test cases and ensure the ApplicationContext loads successfully.
   - [x] Identify proper Distance Enum for PgVectorStore
   - [x] Fix missing Gemini Starter in M5 by fully migrating to OpenAIcts (pom.xml dependencies)
- [x] Provide expert feedback and action plan to the user
- [x] Implement Chunks Explorer (Backend `ChunkVisualizationController` + Frontend `Plotly 3D`)
- [x] Implement Animated Pipeline SVG (Frontend Tailwind Animations)
