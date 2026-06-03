package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.EvidenciaMultimedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvidenciaMultimediaRepository extends JpaRepository<EvidenciaMultimedia, Long> {
}