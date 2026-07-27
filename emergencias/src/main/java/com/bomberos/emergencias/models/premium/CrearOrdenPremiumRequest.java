package com.bomberos.emergencias.models.premium;

public record CrearOrdenPremiumRequest(
        String nombres,
        String email,
        String telefono,
        String identificacion,
        String tipoEstablecimiento,
        String nombreEstablecimiento,
        String direccion,
        String ciudad,
        String provincia,
        String referencia,
        Double latitud,
        Double longitud,
        String proveedorPago
) {}
