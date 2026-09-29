package com.quaysidepay.sepagateway;


import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;


@TestConfiguration
class PostgresTestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer(){
        return new PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("teste")
                .withUsername("teste")
                .withPassword("teste");
    }


}
