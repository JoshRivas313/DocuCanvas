package com.docucanvas.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Almacenamiento del binario original de un documento (el archivo tal cual lo
 * subió el usuario), desacoplado de la metadata del agregado {@link
 * com.docucanvas.domain.model.Document}. Separar este puerto evita que la
 * tabla de metadata cargue bytes pesados en operaciones que no los necesitan
 * (listados, cambios de estado durante la ingesta).
 */
public interface BlobStoragePort {

    void store(UUID documentId, byte[] content);

    Optional<byte[]> find(UUID documentId);
}
