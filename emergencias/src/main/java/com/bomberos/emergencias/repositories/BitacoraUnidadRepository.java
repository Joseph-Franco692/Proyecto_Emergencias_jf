package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.BitacoraUnidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BitacoraUnidadRepository extends JpaRepository<BitacoraUnidad, Long> {
    List<BitacoraUnidad> findAllByOrderByFechaHoraDesc();
}
