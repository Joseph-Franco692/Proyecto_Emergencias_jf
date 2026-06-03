package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.ReporteCiudadano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReporteCiudadanoRepository extends JpaRepository<ReporteCiudadano, Long> {
}
