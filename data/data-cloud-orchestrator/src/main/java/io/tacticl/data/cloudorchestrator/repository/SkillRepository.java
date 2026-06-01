package io.tacticl.data.cloudorchestrator.repository;

import io.tacticl.data.cloudorchestrator.entity.Skill;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface SkillRepository extends MongoRepository<Skill, String> {
    List<Skill> findByActive(boolean active);
    List<Skill> findByIdIn(Collection<String> ids);
}
