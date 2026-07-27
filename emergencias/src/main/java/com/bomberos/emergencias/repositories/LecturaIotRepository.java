package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.LecturaIot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturaIotRepository extends JpaRepository<LecturaIot, Long> {
    List<LecturaIot> findByReporteIdOrderByFechaHoraDesc(Long reporteId);
    List<LecturaIot> findTop5BySesionIdOrderByFechaHoraDesc(Long sesionId);
    Optional<LecturaIot> findFirstBySesionIdOrderByFechaHoraAsc(Long sesionId);
    Optional<LecturaIot> findFirstBySesionIdOrderByFechaHoraDesc(Long sesionId);
}
