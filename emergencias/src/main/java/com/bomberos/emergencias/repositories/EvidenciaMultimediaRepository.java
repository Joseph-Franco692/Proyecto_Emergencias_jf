package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.EvidenciaMultimedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvidenciaMultimediaRepository extends JpaRepository<EvidenciaMultimedia, Long> {
    List<EvidenciaMultimedia> findByReporteCiudadanoId(Long id);
    Optional<EvidenciaMultimedia> findByStorageKey(String storageKey);
}
