package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.OrdenPremium;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrdenPremiumRepository extends JpaRepository<OrdenPremium, Long> {
    Optional<OrdenPremium> findByCodigoOrden(String codigoOrden);
    Optional<OrdenPremium> findByProveedorOrdenId(String proveedorOrdenId);
    Optional<OrdenPremium> findByProveedorTransaccionId(String proveedorTransaccionId);
}
