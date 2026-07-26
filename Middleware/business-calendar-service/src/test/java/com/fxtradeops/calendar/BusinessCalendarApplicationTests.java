package com.fxtradeops.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context starts without error.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class BusinessCalendarApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts without error.
    }
}
