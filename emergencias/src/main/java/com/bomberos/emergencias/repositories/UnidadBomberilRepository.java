package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.EstadoUnidad;
import com.bomberos.emergencias.models.UnidadBomberil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnidadBomberilRepository extends JpaRepository<UnidadBomberil, Long> {

    List<UnidadBomberil> findByEstado(EstadoUnidad estado);

    List<UnidadBomberil> findByReporteAsignadoId(Long reporteId);
}
