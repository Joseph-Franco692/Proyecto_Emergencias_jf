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
    @JoinColumn(name = "reporte_id", nullable = false)
    private ReporteCiudadano reporteCiudadano;

    @Column(name = "url_archivo", nullable = false)
    private String urlArchivo; // Ruta o URL del archivo estático

    @Column(name = "tipo_archivo", nullable = false, length = 10)
    private String tipoArchivo; // 'FOTO' o 'VIDEO'

    @Column(name = "hash_sha256", length = 64)
    private String hashSha256; // Checksum SHA-256 para integridad y deduplicación distribuida

    @Column(name = "fecha_subida", updatable = false)
    private LocalDateTime fechaSubida = LocalDateTime.now();
}