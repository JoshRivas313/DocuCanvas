package com.docucanvas.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Importación de texto pegado directamente, sin archivo.
 *
 * <p>El límite de {@code content} es el equivalente textual del
 * {@code max-file-size: 10MB} que ya protege la subida de archivos: sin él,
 * este endpoint era la vía sin cota para meter un corpus arbitrariamente grande
 * en la cola de ingesta.
 */
public record TextImportRequest(
    @NotBlank
    @Size(max = 300, message = "El título no puede superar los 300 caracteres")
    String title,

    @NotBlank
    @Size(max = 1_000_000, message = "El contenido no puede superar 1.000.000 de caracteres")
    String content,

    @Size(max = 10, message = "sourceType no puede superar los 10 caracteres")
    String sourceType
) {}
