package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.SesionIot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SesionIotRepository extends JpaRepository<SesionIot, Long> {
    Optional<SesionIot> findFirstByNodoIdAndEstadoOrderByFechaInicioDesc(String nodoId, String estado);
    Optional<SesionIot> findFirstByReporteIdAndEstadoOrderByFechaInicioDesc(Long reporteId, String estado);
    Optional<SesionIot> findFirstByReporteIdOrderByFechaInicioDesc(Long reporteId);
    List<SesionIot> findByReporteIdOrderByFechaInicioDesc(Long reporteId);
}
