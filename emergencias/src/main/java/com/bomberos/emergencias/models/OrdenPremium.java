package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ordenes_premium", indexes = {
        @Index(name = "idx_orden_premium_codigo", columnList = "codigoOrden", unique = true),
        @Index(name = "idx_orden_premium_proveedor", columnList = "proveedorOrdenId")
})
@Data
public class OrdenPremium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String codigoOrden;

    @Column(nullable = false, length = 40)
    private String planCodigo = "PREVENCION_IOT_HOGAR";

    @Column(nullable = false)
    private Integer montoCentavos;

    @Column(nullable = false, length = 3)
    private String moneda = "USD";

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(length = 30)
    private String identificacion;

    @Column(nullable = false, length = 40)
    private String tipoEstablecimiento;

    @Column(length = 140)
    private String nombreEstablecimiento;

    @Column(nullable = false, length = 250)
    private String direccion;

    @Column(nullable = false, length = 100)
    private String ciudad;

    @Column(nullable = false, length = 100)
    private String provincia;

    @Column(length = 300)
    private String referencia;

    private Double latitud;
    private Double longitud;

    @Column(nullable = false, length = 20)
    private String proveedorPago;

    @Column(nullable = false, length = 20)
    private String estadoPago = "PENDIENTE";

    @Column(nullable = false, length = 30)
    private String estadoInstalacion = "PENDIENTE_PAGO";

    @Column(length = 100)
    private String proveedorOrdenId;

    @Column(length = 100)
    private String proveedorTransaccionId;

    @Column(length = 500)
    private String urlPago;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    private LocalDateTime fechaActualizacion = LocalDateTime.now();
    private LocalDateTime fechaPago;
    private LocalDateTime fechaLimiteInstalacion;

    @PreUpdate
    void actualizarFecha() {
        fechaActualizacion = LocalDateTime.now();
    }
}
