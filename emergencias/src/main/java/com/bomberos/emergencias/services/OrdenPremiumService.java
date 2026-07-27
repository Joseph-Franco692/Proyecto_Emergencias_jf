package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.OrdenPremium;
import com.bomberos.emergencias.models.premium.CrearOrdenPremiumRequest;
import com.bomberos.emergencias.models.premium.OrdenPremiumResponse;
import com.bomberos.emergencias.repositories.OrdenPremiumRepository;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrdenPremiumService {

    private final OrdenPremiumRepository repository;
    private final PasarelaPagoService pasarela;

    @Value("${premium.plan.amount-cents:4999}")
    private int planAmountCents;

    public Map<String, Object> configuracionPublica() {
        return Map.of(
                "planCodigo", "PREVENCION_IOT_HOGAR",
                "nombre", "Plan Premium de Prevención",
                "montoCentavos", planAmountCents,
                "moneda", "USD",
                "paypalClientId", pasarela.paypalClientId(),
                "plazoInstalacionDias", 2
        );
    }

    @Transactional
    public OrdenPremiumResponse crear(CrearOrdenPremiumRequest request) {
        validar(request);
        OrdenPremium orden = new OrdenPremium();
        orden.setCodigoOrden(generarCodigo());
        orden.setMontoCentavos(planAmountCents);
        orden.setNombres(clean(request.nombres()));
        orden.setEmail(clean(request.email()).toLowerCase(Locale.ROOT));
        orden.setTelefono(clean(request.telefono()));
        orden.setIdentificacion(cleanNullable(request.identificacion()));
        orden.setTipoEstablecimiento(clean(request.tipoEstablecimiento()).toUpperCase(Locale.ROOT));
        orden.setNombreEstablecimiento(cleanNullable(request.nombreEstablecimiento()));
        orden.setDireccion(clean(request.direccion()));
        orden.setCiudad(clean(request.ciudad()));
        orden.setProvincia(clean(request.provincia()));
        orden.setReferencia(cleanNullable(request.referencia()));
        orden.setLatitud(request.latitud());
        orden.setLongitud(request.longitud());
        orden.setProveedorPago(clean(request.proveedorPago()).toUpperCase(Locale.ROOT));
        repository.save(orden);

        orden.setProveedorOrdenId(pasarela.crearOrdenPaypal(
                orden.getCodigoOrden(), orden.getMontoCentavos(), orden.getMoneda()));
        return OrdenPremiumResponse.desde(repository.save(orden));
    }

    @Transactional
    public OrdenPremiumResponse capturarPaypal(String codigo, String paypalOrderId) {
        OrdenPremium orden = obtener(codigo);
        if (!"PAYPAL".equals(orden.getProveedorPago())) throw new IllegalArgumentException("La orden no pertenece a PayPal");
        if ("PAGADO".equals(orden.getEstadoPago())) return OrdenPremiumResponse.desde(orden);
        if (paypalOrderId == null || !paypalOrderId.equals(orden.getProveedorOrdenId())) {
            throw new IllegalArgumentException("La orden de PayPal no coincide");
        }
        JsonNode capture;
        try {
            capture = pasarela.capturarPaypal(paypalOrderId);
        } catch (RuntimeException captureError) {
            // PayPal puede haber completado el cobro y perderse la respuesta.
            // Consultar la orden hace el proceso idempotente y evita cobrar dos veces.
            capture = pasarela.consultarOrdenPaypal(paypalOrderId);
        }
        if (!pasarela.validarCapturaPaypal(capture, codigo, orden.getMontoCentavos(), orden.getMoneda())) {
            // Una consulta posterior ofrece el estado definitivo incluso cuando la
            // respuesta inmediata de capture no contiene todos los campos.
            JsonNode estadoOficial = pasarela.consultarOrdenPaypal(paypalOrderId);
            if (!pasarela.validarCapturaPaypal(
                    estadoOficial, codigo, orden.getMontoCentavos(), orden.getMoneda())) {
                orden.setEstadoPago("FALLIDO");
                repository.save(orden);
                throw new IllegalStateException("PayPal no confirmó el pago como COMPLETED");
            }
            capture = estadoOficial;
        }
        return OrdenPremiumResponse.desde(marcarPagada(orden, pasarela.paypalTransactionId(capture)));
    }

    @Transactional(readOnly = true)
    public OrdenPremiumResponse consultar(String codigo, String email) {
        OrdenPremium orden = obtener(codigo);
        if (email == null || !orden.getEmail().equalsIgnoreCase(email.trim())) {
            throw new IllegalArgumentException("Los datos de consulta no coinciden");
        }
        return OrdenPremiumResponse.desde(orden);
    }

    @Transactional
    public OrdenPremiumResponse reconciliarPaypal(String codigo, String email) {
        OrdenPremium orden = obtener(codigo);
        if (email == null || !orden.getEmail().equalsIgnoreCase(email.trim())) {
            throw new IllegalArgumentException("Los datos de la orden no coinciden");
        }
        if ("PAGADO".equals(orden.getEstadoPago())) return OrdenPremiumResponse.desde(orden);
        JsonNode estado = pasarela.consultarOrdenPaypal(orden.getProveedorOrdenId());
        if (!pasarela.validarCapturaPaypal(
                estado, codigo, orden.getMontoCentavos(), orden.getMoneda())) {
            throw new IllegalStateException("PayPal todavía no reporta el pago como COMPLETED");
        }
        return OrdenPremiumResponse.desde(
                marcarPagada(orden, pasarela.paypalTransactionId(estado)));
    }

    private OrdenPremium marcarPagada(OrdenPremium orden, String transactionId) {
        if ("PAGADO".equals(orden.getEstadoPago())) return orden;
        LocalDateTime now = LocalDateTime.now();
        orden.setEstadoPago("PAGADO");
        orden.setEstadoInstalacion("POR_PROGRAMAR");
        orden.setProveedorTransaccionId(transactionId);
        orden.setFechaPago(now);
        orden.setFechaLimiteInstalacion(now.plusDays(2));
        return repository.save(orden);
    }

    private OrdenPremium obtener(String codigo) {
        return repository.findByCodigoOrden(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Orden premium no encontrada"));
    }

    private void validar(CrearOrdenPremiumRequest r) {
        required(r.nombres(), "nombres");
        required(r.email(), "correo");
        required(r.telefono(), "teléfono");
        required(r.tipoEstablecimiento(), "tipo de establecimiento");
        required(r.direccion(), "dirección");
        required(r.ciudad(), "ciudad");
        required(r.provincia(), "provincia");
        required(r.proveedorPago(), "forma de pago");
        if (!r.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Correo electrónico inválido");
        if (!r.telefono().replaceAll("\\D", "").matches("\\d{10,15}")) throw new IllegalArgumentException("Teléfono inválido");
        String provider = r.proveedorPago().toUpperCase(Locale.ROOT);
        if (!provider.equals("PAYPAL")) throw new IllegalArgumentException("La demostración solo admite PayPal");
    }

    private void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El campo " + name + " es obligatorio");
    }

    private String generarCodigo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return "PREV-" + date + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String clean(String value) { return value.trim(); }
    private String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

}
