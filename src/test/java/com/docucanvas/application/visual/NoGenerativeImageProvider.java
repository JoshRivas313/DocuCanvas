package com.docucanvas.application.visual;

import com.docucanvas.application.port.out.GenerativeImagePort;

import java.util.Optional;

/**
 * Doble de test que representa el escenario por defecto del proyecto: no hay
 * ningún proveedor de generación de imágenes configurado.
 *
 * <p>Existe como clase con nombre, en vez de como un mock configurado en cada
 * test, porque es el estado normal de la aplicación —arrancar sin credenciales
 * externas— y merece ser nombrado como tal.
 */
public final class NoGenerativeImageProvider implements GenerativeImagePort {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<String> generate(String visualPrompt) {
        return Optional.empty();
    }

    @Override
    public String providerName() {
        return "ninguno";
    }
}
