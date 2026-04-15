package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Device;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeviceRepository extends MongoRepository<Device, String> {
    List<Device> findByUserId(String userId);
}
