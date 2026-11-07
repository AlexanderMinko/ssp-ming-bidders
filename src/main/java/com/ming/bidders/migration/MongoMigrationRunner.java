package com.ming.bidders.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies the Liquibase changelogs to Mongo at startup, before {@code BidderCatalog} loads (Order 1 vs 2).
 *
 * <p>Boot's Liquibase auto-config is JDBC-only, so this drives Liquibase directly through the
 * {@code liquibase-mongodb} extension. It uses the same {@link MongoConnectionDetails} as Spring Data, so
 * migrations and reads always hit the same database. {@code DATABASECHANGELOGLOCK} keeps concurrent pods from
 * double-applying; {@code DATABASECHANGELOG} makes a restart a no-op. A failure fails startup.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bidders.migration.enabled", havingValue = "true", matchIfMissing = true)
public class MongoMigrationRunner implements ApplicationRunner {

    static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private final MongoConnectionDetails connectionDetails;

    @Override
    public void run(ApplicationArguments args) {
        var accessor = new ClassLoaderResourceAccessor();
        var url = connectionDetails.getConnectionString().getConnectionString();
        log.info("Running Mongo migrations: {}", MASTER_CHANGELOG);
        try (var database = DatabaseFactory.getInstance().openDatabase(url, null, null, null, accessor);
             var liquibase = new Liquibase(MASTER_CHANGELOG, accessor, database)) {
            liquibase.update(new Contexts(), new LabelExpression());
        } catch (LiquibaseException exception) {
            throw new IllegalStateException("Mongo migration failed: " + MASTER_CHANGELOG, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Mongo migration failed to close cleanly", exception);
        }
        log.info("Mongo migrations complete");
    }
}
