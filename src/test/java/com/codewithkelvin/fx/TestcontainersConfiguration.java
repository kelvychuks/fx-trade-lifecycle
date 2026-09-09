package com.codewithkelvin.fx;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration tests run against real PostgreSQL rather than an in-memory
 * database. Half the value of these tests is proving the Flyway migrations
 * apply and the entities match the schema they produce — H2 would happily
 * accept DDL that PostgreSQL rejects, which is the opposite of useful.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
