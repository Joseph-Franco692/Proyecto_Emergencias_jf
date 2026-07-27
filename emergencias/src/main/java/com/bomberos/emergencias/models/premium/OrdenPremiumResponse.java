package com.bomberos.emergencias.models.premium;

import com.bomberos.emergencias.models.OrdenPremium;

import java.time.LocalDateTime;

public record OrdenPremiumResponse(
        String codigoOrden,
        String planCodigo,
        Integer montoCentavos,
        String moneda,
        String proveedorPago,
        String estadoPago,
        String estadoInstalacion,
        String proveedorOrdenId,
        String urlPago,
        LocalDateTime fechaPago,
        LocalDateTime fechaLimiteInstalacion,
        String mensaje
) {
    public static OrdenPremiumResponse desde(OrdenPremium orden) {
        String mensaje = "PAGADO".equals(orden.getEstadoPago())
                ? "Pago confirmado. Nuestros profesionales realizarán la instalación en un plazo máximo de 2 días."
                : "Orden creada. Completa el pago para programar la instalación.";
        return new OrdenPremiumResponse(
                orden.getCodigoOrden(), orden.getPlanCodigo(), orden.getMontoCentavos(),
                orden.getMoneda(), orden.getProveedorPago(), orden.getEstadoPago(),
                orden.getEstadoInstalacion(), orden.getProveedorOrdenId(), orden.getUrlPago(),
                orden.getFechaPago(), orden.getFechaLimiteInstalacion(), mensaje
        );
    }
}
