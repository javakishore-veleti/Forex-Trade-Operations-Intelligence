package com.fxtradeops.calendar;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration for integration tests.
 * Uses the PostgreSQL instance configured in application-test.yml.
 * In environments with working Docker/Testcontainers, this can be swapped
 * for a @ServiceConnection PostgreSQLContainer bean.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {
    // Configuration is driven by application-test.yml datasource properties.
    // The running PostgreSQL container on port 5432 is used for integration tests.
}
