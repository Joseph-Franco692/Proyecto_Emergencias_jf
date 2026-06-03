package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "evidencias_multimedia")
public class EvidenciaMultimedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RELACIÓN: Muchas evidencias multimedia pertenecen a un único Reporte Ciudadano
    @ManyToOne
    // nullable = false exige que toda foto a la fuerza deba estar amarrada a un reporte existente
    @JoinColumn(name = "reporte_id", nullable = false)
    private ReporteCiudadano reporteCiudadano;

    @Column(name = "url_archivo", nullable = false)
    private String urlArchivo; // Aquí guardaremos la ruta física del archivo (ej: "/uploads/foto1.jpg")

    @Column(name = "tipo_archivo", nullable = false, length = 10)
    private String tipoArchivo; // Guardará explícitamente cadenas como 'FOTO' o 'VIDEO'

    @Column(name = "fecha_subida", updatable = false)
    private LocalDateTime fechaSubida = LocalDateTime.now();
}