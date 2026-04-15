package io.tacticl.data.connections.base;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseMongoEntityTest {

    @Test
    void idField_hasIdAnnotation() throws NoSuchFieldException {
        Field idField = BaseMongoEntity.class.getDeclaredField("id");
        assertThat(idField.isAnnotationPresent(Id.class))
            .as("id field must carry @Id")
            .isTrue();
        assertThat(idField.getType()).isEqualTo(String.class);
    }

    @Test
    void createdAtField_hasCreatedDateAnnotation() throws NoSuchFieldException {
        Field createdAtField = BaseMongoEntity.class.getDeclaredField("createdAt");
        assertThat(createdAtField.isAnnotationPresent(CreatedDate.class))
            .as("createdAt field must carry @CreatedDate")
            .isTrue();
        assertThat(createdAtField.getType()).isEqualTo(Instant.class);
    }

    @Test
    void updatedAtField_hasLastModifiedDateAnnotation() throws NoSuchFieldException {
        Field updatedAtField = BaseMongoEntity.class.getDeclaredField("updatedAt");
        assertThat(updatedAtField.isAnnotationPresent(LastModifiedDate.class))
            .as("updatedAt field must carry @LastModifiedDate")
            .isTrue();
        assertThat(updatedAtField.getType()).isEqualTo(Instant.class);
    }

    @Test
    void setId_roundTrips() {
        var entity = new TestEntity("hello");
        entity.setId("abc-123");
        assertThat(entity.getId()).isEqualTo("abc-123");
    }

    @Test
    void newEntity_hasNullAuditDatesAndId() {
        var entity = new TestEntity("world");
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void baseMongoEntity_isAbstract() {
        assertThat(java.lang.reflect.Modifier.isAbstract(BaseMongoEntity.class.getModifiers()))
            .as("BaseMongoEntity must be abstract")
            .isTrue();
    }
}
