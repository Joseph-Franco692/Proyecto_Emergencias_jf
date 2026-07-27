package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.models.premium.CapturarPaypalRequest;
import com.bomberos.emergencias.models.premium.CrearOrdenPremiumRequest;
import com.bomberos.emergencias.models.premium.OrdenPremiumResponse;
import com.bomberos.emergencias.services.OrdenPremiumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
public class PremiumController {

    private final OrdenPremiumService service;

    @GetMapping("/plan")
    public Map<String, Object> plan() {
        return service.configuracionPublica();
    }

    @PostMapping("/ordenes")
    public ResponseEntity<OrdenPremiumResponse> crear(@RequestBody CrearOrdenPremiumRequest request) {
        return ResponseEntity.ok(service.crear(request));
    }

    @PostMapping("/ordenes/{codigo}/paypal/capturar")
    public OrdenPremiumResponse capturarPaypal(
            @PathVariable String codigo,
            @RequestBody CapturarPaypalRequest request) {
        return service.capturarPaypal(codigo, request.paypalOrderId());
    }

    @GetMapping("/ordenes/{codigo}")
    public OrdenPremiumResponse consultar(@PathVariable String codigo, @RequestParam String email) {
        return service.consultar(codigo, email);
    }

    @PostMapping("/ordenes/{codigo}/paypal/reconciliar")
    public OrdenPremiumResponse reconciliarPaypal(
            @PathVariable String codigo,
            @RequestParam String email) {
        return service.reconciliarPaypal(codigo, email);
    }

}
