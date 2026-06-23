package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Entity
@Table(name = "unidades_bomberiles")
public class UnidadBomberil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre; // e.g. "B-01 Autobomba"

    @Column(nullable = false, length = 80)
    private String tipo; // e.g. "Ataque contra incendios"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoUnidad estado = EstadoUnidad.DISPONIBLE;

    // Relación ManyToOne opcional: la unidad puede estar asignada a un reporte
    // JsonIgnoreProperties evita el ciclo infinito de serialización JSON
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporte_asignado_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "incidente"})
    private ReporteCiudadano reporteAsignado;
}
