package com.docucanvas.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;

/**
 * Manejador global de excepciones para la API REST de DocuCanvas.
 *
 * <p>Convierte excepciones en respuestas HTTP coherentes usando el estándar
 * RFC 7807 (Problem Details), evitando que el cliente reciba stack traces
 * en producción y mejorando la experiencia de depuración durante la demo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Recursos estáticos no encontrados (favicon.ico, etc.) → 404 silencioso.
     * Evita ERROR ruidoso en los logs durante la demo.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Recurso estático no encontrado (ignorado): {}", ex.getResourcePath());
        return ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    }

    /**
     * Captura errores de validación de parámetros (@Valid en los controllers).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validación fallida: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://docucanvas.io/errors/validation"));
        problem.setTitle("Error de validación");
        problem.setDetail("Uno o más campos de la petición son inválidos.");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList());
        return problem;
    }

    /**
     * Captura archivos que superan el límite configurado en multipart.max-file-size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Archivo demasiado grande: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        problem.setType(URI.create("https://docucanvas.io/errors/file-too-large"));
        problem.setTitle("Archivo demasiado grande");
        problem.setDetail("El archivo supera el tamaño máximo permitido (10 MB).");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura errores de comunicación con la API de OpenAI / ImageModel.
     * Evita que el usuario vea la API key u otros detalles sensibles.
     */
    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    public ProblemDetail handleOpenAiError(org.springframework.web.client.RestClientException ex) {
        log.error("Error en llamada a servicio externo de IA: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problem.setType(URI.create("https://docucanvas.io/errors/ai-service-unavailable"));
        problem.setTitle("Servicio de IA no disponible");
        problem.setDetail("El modelo de IA no pudo procesar la solicitud. Intenta de nuevo en unos segundos.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Fallback genérico: captura cualquier excepción no controlada.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Error inesperado: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://docucanvas.io/errors/internal"));
        problem.setTitle("Error interno del servidor");
        problem.setDetail("Ocurrió un error inesperado. El equipo ha sido notificado.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
