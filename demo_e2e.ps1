# DocuCanvas E2E Demo Script
# Escenario: "No leas la documentación, haz que Spring AI te la dibuje"

$baseUrl = "http://localhost:8080/api/v1"

Write-Host "🚀 Iniciando Demo E2E de DocuCanvas..." -ForegroundColor Cyan

# 1. Ingesta de texto
Write-Host "`n1. Alimentando el cerebro con conocimiento nuevo..." -ForegroundColor Yellow
$ingestBody = @{
    title = "Manual de Supervivencia en Marte"
    content = "Para sobrevivir en Marte, es vital construir domos de cristal reforzado. El cultivo hidropónico de papas es la base de la alimentación. El traje espacial debe revisarse cada 6 horas para detectar fugas de presión."
    sourceType = "TEXT"
} | ConvertTo-Json

$ingestRes = Invoke-RestMethod -Uri "$baseUrl/documents/import-text" -Method Post -Body $ingestBody -ContentType "application/json"
$docId = $ingestRes.documentId
Write-Host "✅ Documento enviado. ID: $docId"

# 2. Esperar procesamiento (Simulado, en local es casi instantáneo)
Write-Host "⌛ Esperando a que Spring AI indexe el contenido..."
Start-Sleep -Seconds 3

# 3. Pregunta RAG + Imagen
Write-Host "`n3. Consultando a la IA con RAG + Generación Visual..." -ForegroundColor Yellow
$questionBody = @{
    question = "¿Cómo debo sobrevivir en Marte según el manual?"
    maxChunks = 3
} | ConvertTo-Json

$startTime = Get-Date
$response = Invoke-RestMethod -Uri "$baseUrl/questions/ask" -Method Post -Body $questionBody -ContentType "application/json"
$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

Write-Host "`n🤖 IA Responde:" -ForegroundColor Green
Write-Host $response.answer
Write-Host "`n📊 Fuentes utilizadas: $($response.sources -join ', ')"
Write-Host "⏱️ Tiempo de respuesta: $duration segundos"

# 4. Mostrar URL de Imagen
Write-Host "`n🎨 ¡Spring AI ha dibujado la respuesta!" -ForegroundColor Magenta
Write-Host "🖼️ URL de la imagen: $($response.imageUrl)"

Write-Host "`n✨ Demo completada con éxito. ¡Listo para la charla! ✨" -ForegroundColor Cyan
