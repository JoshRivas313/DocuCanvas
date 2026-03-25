$ErrorActionPreference = "Stop"

Write-Host "Verificando si la app está viva..."
Start-Sleep -Seconds 15

$body = @{
    title = "Test RAG"
    content = "Spring AI permite integrar modelos de IA con Java. RAG usa pgvector para recuperar contexto relevante."
    sourceType = "TEXT"
    tags = @("test", "rag")
} | ConvertTo-Json -Depth 3

Write-Host "1. Subiendo documento..."
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/import-text" -Method POST -Body $body -ContentType "application/json"
$jobId = $response.jobId
Write-Host "Job Creado: $jobId"

Write-Host "2. Esperando que termine de procesar (10s)..."
Start-Sleep -Seconds 10
$jobStatus = Invoke-RestMethod "http://localhost:8080/api/v1/ingestion-jobs/$jobId"
Write-Host "Estado del Job: $($jobStatus.status)"

Write-Host "3. Listando documentos..."
$docs = Invoke-RestMethod "http://localhost:8080/api/v1/documents?limit=5"
$docId = $docs.items[0].documentId
Write-Host "Documento listado ID: $docId"

Write-Host "4. Preguntando RAG..."
$qbody = @{
    question = "¿Qué es RAG?"
    documentIds = @($docId)
    topK = 3
    includeSources = $true
} | ConvertTo-Json -Depth 3

$answer = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/questions" -Method POST -Body $qbody -ContentType "application/json"
Write-Host "RESPUESTA RAG:"
Write-Host $answer.answer
Write-Host "FUENTES:"
Write-Host ($answer.sources | Out-String)
