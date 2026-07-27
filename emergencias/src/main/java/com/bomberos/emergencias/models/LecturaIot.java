package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "lecturas_iot")
@Data
public class LecturaIot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nodo_id")
    private String nodoId;

    @Column(name = "reporte_id")
    private Long reporteId;

    @Column(name = "sesion_id")
    private Long sesionId;

    @Column(name = "unidad_id")
    private Long unidadId;

    @Column(name = "operador")
    private String operador;

    @Column(name = "nivel_gas")
    private Integer nivelGas;

    @Column(name = "umbral_advertencia")
    private Integer umbralAdvertencia;

    @Column(name = "umbral_peligro")
    private Integer umbralPeligro;

    @Column(name = "estado_aire")
    private String estadoAire;               // RESPIRABLE, PRECAUCION, CRITICO

    @Column(name = "evaluacion_habitabilidad")
    private String evaluacionHabitabilidad;   // HABITABLE, NO_HABITABLE, EVALUANDO

    @Column(name = "evento")
    private String evento;                   // INICIO_SESION, TELEMETRIA, FIN_SESION

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @PrePersist
    public void prePersist() {
        if (this.fechaHora == null) {
            this.fechaHora = LocalDateTime.now();
        }
    }
}
