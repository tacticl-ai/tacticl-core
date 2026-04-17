package io.strategiz.social.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Enables Spring Data MongoDB repository scanning for all tacticl modules.
 * Required because the @SpringBootApplication class is in io.strategiz.social.application
 * but AutoConfigurationPackages only registers that base package — not io.tacticl.
 * All MongoDB repositories live in io.tacticl.data.* so we must scan explicitly.
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {"io.tacticl", "io.cidadel.data"})
public class MongoConfig {
}
