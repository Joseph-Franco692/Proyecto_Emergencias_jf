package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_iot")
@Data
public class SesionIot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nodoId;

    @Column(nullable = false)
    private Long reporteId;

    @Column(nullable = false)
    private Long unidadId;

    @Column(nullable = false, length = 150)
    private String operador;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVA";

    @Column(length = 30)
    private String resultadoFinal = "EVALUANDO";

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();

    private LocalDateTime fechaFin;
}
