package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reportes_ciudadanos")
public class ReporteCiudadano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RELACIÓN: Muchos reportes ciudadanos pueden pertenecer a un mismo Incidente Central
    @ManyToOne // Relación Many-To-One (Muchos a Uno)
    @JoinColumn(name = "incidente_id") // Define el nombre de la FK (Foreign Key) en la tabla
    private Incidente incidente; // Si es null, significa que aún ningún bombero lo ha vinculado a un incidente global

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    // precision = 10 y scale = 8 significa: 10 dígitos en total, de los cuales 8 son decimales (Exactitud GPS)
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal latitud;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal longitud;

    @Column(name = "celular_reportero", length = 15)
    private String celularReportero;

    @Column(name = "fecha_reporte", updatable = false)
    private LocalDateTime fechaReporte = LocalDateTime.now();
}