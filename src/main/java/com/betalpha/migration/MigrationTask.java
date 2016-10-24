package com.betalpha.migration;

import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.List;

import static com.betalpha.migration.util.Ensure.notNull;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * The migration task is managing the database migrations. It checks which
 * schema version is in the database and retrieves the migrations that
 * need to be applied from the repository. Those migrations are than
 * executed against the database.
 *
 * @author Patrick Kranz
 */
public class MigrationTask {
    private static final Logger LOGGER = getLogger(MigrationTask.class);

    private final Database database;
    private final MigrationRepository repository;

    /**
     * Creates a migration task that uses the given database and repository.
     *
     * @param database   the database that should be migrated
     * @param repository the repository that contains the migration scripts
     */
    public MigrationTask(Database database, MigrationRepository repository) {
        this.database = notNull(database, "database");
        this.repository = notNull(repository, "repository");
    }

    /**
     * Start the actual migration. Take the version of the database, get all required migrations and executeScript them or do
     * nothing if the DB is already up to date.
     * <p>
     * At the end the underlying database instance is closed.
     *
     * @throws MigrationException if a migration fails
     */
    public void migrate() {
        if (databaseIsUpToDate()) {
            LOGGER.info(format("Keyspace %s is already up to date at version %d", database.getKeyspaceName(),
                    database.getVersion()));
            return;
        }

        List<DbMigration> migrations = repository.getMigrationsSinceVersion(database.getVersion());
        List<String> profiles = Lists.newArrayList(repository.getEnvironment().getActiveProfiles());
        String command = "docker exec -it casscon cqlsh -f ";
        boolean custom=false;
        if (profiles.contains("custom")) {
            command = "cqlsh -f ";
            custom=true;
        }
        String finalCommand = command;
        boolean finalCustom = custom;
        migrations.forEach(dbMigration -> {
            database.executeScript(dbMigration);
            database.executeData(dbMigration, repository.getServerDataPath(), finalCommand, finalCustom);

        });
        LOGGER.info(format("Migrated keyspace %s to version %d", database.getKeyspaceName(), database.getVersion()));
        database.close();
    }

    private boolean databaseIsUpToDate() {
        return database.getVersion() >= repository.getLatestVersion();
    }
}
