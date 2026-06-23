package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bitacoras_unidad")
public class BitacoraUnidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unidad_id", nullable = false)
    private UnidadBomberil unidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reporte_id", nullable = true)
    private ReporteCiudadano reporte;

    @Column(nullable = false, length = 100)
    private String operador;

    @Column(nullable = false, length = 500)
    private String personalInvolucrado;

    @Column(columnDefinition = "TEXT")
    private String novedades;

    @Column(nullable = false)
    private LocalDateTime fechaHora;
}
