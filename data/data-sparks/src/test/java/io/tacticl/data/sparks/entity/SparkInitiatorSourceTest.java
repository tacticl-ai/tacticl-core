package io.tacticl.data.sparks.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparkInitiatorSourceTest {

    @Test
    void sparkStampsInitiatorSourceAndUser() {
        var s = Spark.create("owner-1", "hello");
        s.setInitiatorSource(SparkInitiatorSource.TELEGRAM_GROUP);
        s.setInitiatorUserId("u-42");
        assertEquals(SparkInitiatorSource.TELEGRAM_GROUP, s.getInitiatorSource());
        assertEquals("u-42", s.getInitiatorUserId());
    }
}
