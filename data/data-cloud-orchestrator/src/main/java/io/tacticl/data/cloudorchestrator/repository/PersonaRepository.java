package io.tacticl.data.cloudorchestrator.repository;

import io.tacticl.data.cloudorchestrator.entity.Persona;
import io.tacticl.data.cloudorchestrator.entity.PersonaFamily;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PersonaRepository extends MongoRepository<Persona, String> {
    List<Persona> findByFamily(PersonaFamily family);
    Optional<Persona> findByIdAndActive(String id, boolean active);
}
