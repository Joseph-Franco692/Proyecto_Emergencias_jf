package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.EstadoUnidad;
import com.bomberos.emergencias.models.UnidadBomberil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface UnidadBomberilRepository extends JpaRepository<UnidadBomberil, Long> {

    List<UnidadBomberil> findByEstado(EstadoUnidad estado);
    List<UnidadBomberil> findByEstadoAndOperadorUltimoHeartbeatAfter(
            EstadoUnidad estado,
            LocalDateTime heartbeatMinimo
    );

    List<UnidadBomberil> findByReporteAsignadoId(Long reporteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UnidadBomberil> findWithLockById(Long id);
}
