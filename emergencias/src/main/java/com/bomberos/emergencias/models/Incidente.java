package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name="incidentes")
public class Incidente {
    @Id // Define que este atributo es la Clave Primaria (Primary Key)
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Configura el campo como Autoincrementable (SERIAL en Postgres)
    private Long id;

    // Mapea la columna, define que es única, no permite nulos y longitud máxima de 20 caracteres
    @Column(name = "codigo_emergencia", unique = true, nullable = false, length = 20)
    private String codigoEmergencia;

    @Column(name = "tipo_emergencia", nullable = false, length = 50)
    private String tipoEmergencia;

    @Column(length = 20)
    private String estado = "PENDIENTE"; // Por defecto, todo incidente inicia como PENDIENTE

    @Column(length = 15)
    private String prioridad = "MEDIA"; // Prioridad inicial por defecto

    @Column(name = "resumen_ia", columnDefinition = "TEXT") // 'TEXT' permite guardar textos muy largos (el análisis de la IA)
    private String resumenIa;

    @Column(name = "fecha_creacion", updatable = false) // 'updatable = false' evita que esta fecha se modifique en los UPDATE
    private LocalDateTime fechaCreacion = LocalDateTime.now(); // Captura la fecha y hora actual del servidor al crearse

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

}
