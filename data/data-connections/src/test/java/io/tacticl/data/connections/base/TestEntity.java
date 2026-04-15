package io.tacticl.data.connections.base;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("test_entities")
class TestEntity extends BaseMongoEntity {
    private String value;
    TestEntity(String value) { this.value = value; }
    String getValue() { return value; }
}
