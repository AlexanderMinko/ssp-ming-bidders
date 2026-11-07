package com.ming.bidders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/** One Mongo 7 container per test context, wired into spring.mongodb.* via @ServiceConnection. */
@TestConfiguration(proxyBeanMethods = false)
public class MongoContainerConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:7");
    }
}
