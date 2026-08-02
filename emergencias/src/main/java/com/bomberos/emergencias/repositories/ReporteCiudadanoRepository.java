package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.ReporteCiudadano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface ReporteCiudadanoRepository extends JpaRepository<ReporteCiudadano, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReporteCiudadano> findWithLockById(Long id);
}
