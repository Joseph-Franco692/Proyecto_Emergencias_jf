package com.bomberos.emergencias.repositories;

import com.bomberos.emergencias.models.Incidente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // Le dice a Spring que este es un componente de persistencia (Acceso a Datos)
// JpaRepository recibe: <Clase de la Entidad, Tipo de dato de su Clave Primaria>
public interface IncidenteRepository extends JpaRepository<Incidente, Long> {
    // Aquí adentro ya existen métodos listos como: save(), findById(), findAll(), delete()
}