package io.tacticl.data.profile.base;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import static org.assertj.core.api.Assertions.assertThat;

class BaseMongoEntityTest {

    // Concrete subclass for testing
    static class TestEntity extends BaseMongoEntity {}

    @Test
    void id_hasIdAnnotation() throws NoSuchFieldException {
        var field = BaseMongoEntity.class.getDeclaredField("id");
        assertThat(field.isAnnotationPresent(Id.class)).isTrue();
    }

    @Test
    void createdAt_hasCreatedDateAnnotation() throws NoSuchFieldException {
        var field = BaseMongoEntity.class.getDeclaredField("createdAt");
        assertThat(field.isAnnotationPresent(CreatedDate.class)).isTrue();
    }

    @Test
    void updatedAt_hasLastModifiedDateAnnotation() throws NoSuchFieldException {
        var field = BaseMongoEntity.class.getDeclaredField("updatedAt");
        assertThat(field.isAnnotationPresent(LastModifiedDate.class)).isTrue();
    }

    @Test
    void isActive_defaultsToTrue() {
        assertThat(new TestEntity().isActive()).isTrue();
    }

    @Test
    void setActive_togglesFlag() {
        var e = new TestEntity();
        e.setActive(false);
        assertThat(e.isActive()).isFalse();
    }
}
