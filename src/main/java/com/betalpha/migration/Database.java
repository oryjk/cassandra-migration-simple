package com.betalpha.migration;

import com.datastax.driver.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.net.URL;
import java.util.Date;

import static java.lang.String.format;
import static org.cognitor.cassandra.migration.util.Ensure.notNull;
import static org.cognitor.cassandra.migration.util.Ensure.notNullOrEmpty;

/**
 * This class represents the Cassandra database. It is used to retrieve the current version of the database and to
 * executeScript migrations.
 *
 * @author Patrick Kranz
 */
public class Database implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    /**
     * The name of the table that manages the migration scripts
     */
    private static final String SCHEMA_CF = "schema_migration";

    /**
     * Insert statement that logs a migration into the schema_migration table.
     */
    private static final String INSERT_MIGRATION = "insert into %s"
            + "(applied_successful, version,data_successful, script_name, script, executed_at) values(?, ?,?, ?, ?, ?)";

    /**
     * Statement used to create the table that manages the migrations.
     */
    private static final String CREATE_MIGRATION_CF = "CREATE TABLE %s"
            + " (applied_successful boolean, version int,data_successful boolean, script_name varchar, script text,"
            + " executed_at timestamp, PRIMARY KEY (applied_successful, version))";

    /**
     * The query that retrieves current schema version
     */
    private static final String VERSION_QUERY =
            "select version from %s where applied_successful = True "
                    + "order by version desc limit 1";

    /**
     * Error message that is thrown if there is an error during the migration
     */
    private static final String MIGRATION_ERROR_MSG = "Error during migration of script %s while executing '%s'";

    /**
     * The delimiter that is used between two cql statements.
     */
    private static final String STATEMENT_DELIMITER = ";";

    private final String keyspaceName;
    private final Cluster cluster;
    private final Session session;
    private final PreparedStatement logMigrationStatement;

    /**
     * Creates a new instance of the database.
     *
     * @param cluster      the cluster that is connected to a cassandra instance
     * @param keyspaceName the keyspace name that will be managed by this instance
     */
    public Database(Cluster cluster, String keyspaceName) {
        this.cluster = notNull(cluster, "cluster");
        this.keyspaceName = notNullOrEmpty(keyspaceName, "keyspaceName");
        session = cluster.connect(keyspaceName);
        ensureSchemaTable();
        this.logMigrationStatement = session.prepare(format(INSERT_MIGRATION, SCHEMA_CF));
    }

    /**
     * Closes the underlying session object. The cluster will not be touched
     * and will stay open. Call this after all migrations are done.
     * After calling this, this database instance can no longer be used.
     */
    public void close() {
        this.session.close();
    }

    /**
     * Gets the current version of the database schema. This version is taken
     * from the migration table and represent the latest successful entry.
     *
     * @return the current schema version
     */
    public int getVersion() {
        ResultSet resultSet = session.execute(format(VERSION_QUERY, SCHEMA_CF));
        Row result = resultSet.one();
        if (result == null) {
            return 0;
        }
        return result.getInt(0);
    }

    /**
     * Returns the name of the keyspace managed by this instance.
     *
     * @return the name of the keyspace managed by this instance
     */
    public String getKeyspaceName() {
        return this.keyspaceName;
    }

    /**
     * Makes sure the schema migration table exists. If it is not available it will be created.
     */
    private void ensureSchemaTable() {
        if (schemaTablesIsNotExisting()) {
            createSchemaTable();
        }
    }

    private boolean schemaTablesIsNotExisting() {
        return cluster.getMetadata().getKeyspace(keyspaceName).getTable(SCHEMA_CF) == null;
    }

    private void createSchemaTable() {
        session.execute(format(CREATE_MIGRATION_CF, SCHEMA_CF));
    }

    /**
     * Executes the given migration to the database and logs the migration along with the output in the migration table.
     * In case of an error a {@link MigrationException} is thrown with the cause of the error inside.
     *
     * @param migration the migration to be executed.
     * @throws MigrationException if the migration fails
     */
    public void executeScript(DbMigration migration) {
        LOGGER.info("Begin data migration for script.");
        notNull(migration, "migration");
        LOGGER.debug(format("About to executeScript migration %s to version %d", migration.getScriptName(),
                migration.getVersion()));
        String lastStatement = null;
        try {
            for (String statement : migration.getMigrationScript().split(STATEMENT_DELIMITER)) {
                statement = statement.trim();
                lastStatement = statement;
                executeStatement(statement);
            }
            logMigration(migration, true, false);
            LOGGER.debug(format("Successfully applied migration %s to version %d",
                    migration.getScriptName(), migration.getVersion()));
        } catch (Exception exception) {
            logMigration(migration, false, false);
            String errorMessage = format(MIGRATION_ERROR_MSG, migration.getScriptName(), lastStatement);
            throw new MigrationException(errorMessage, exception, migration.getScriptName(), lastStatement);
        } finally {
            LOGGER.info("End data migration for script.");
        }
    }

    public void executeData(DbMigration migration, String folderPath, String command, boolean finalCustom) {
        LOGGER.info("Begin data migration for data, custom profile={}.", finalCustom);
        notNull(folderPath, "migration");
        try {
            File folder;
            if (finalCustom) {
                URL url = getClass().getClassLoader().getResource(folderPath + "/" + migration.getVersion());
                if (ObjectUtils.isEmpty(url)) {
                    LOGGER.info("Invalid path:{}.", folderPath + "/" + migration.getVersion());
                    logMigration(migration, true, null);
                    LOGGER.info("End data migration for data.");
                    return;
                }
                folder = new File(url.getPath());
            } else {
                folder = new File(folderPath + "/" + migration.getVersion());
            }

            if (!folder.isDirectory()) {
                LOGGER.info("Empty file in {}", folderPath + "/" + migration.getVersion());
                logMigration(migration, true, null);
                LOGGER.info("End data migration for data.");
                return;
            }
            File[] files = folder.listFiles();
            LOGGER.info("Has {} file in {}.", files.length, folderPath + "/" + migration.getVersion());
            StringBuilder fileContent = new StringBuilder("use bar;\n");
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                fileContent.append("copy " + file.getName() + " from '" + file.getAbsolutePath() + "';\n");
            }
            String filePath = folder.getAbsolutePath() + "/command.cql";
            LOGGER.info("Command file path={}.", filePath);
            FileWriter fileWriter = new FileWriter(filePath);
            BufferedWriter out = new BufferedWriter(fileWriter);
            out.write(fileContent.toString());
            out.close();
            Process p = Runtime.getRuntime().exec(command + folder.getAbsolutePath() + "/command.cql");
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                LOGGER.info(s);
            }
            while ((s = stdError.readLine()) != null) {
                LOGGER.error("Std ERROR : " + s);
            }
            logMigration(migration, true, true);
            LOGGER.debug(format("Successfully applied migration %s to version %d",
                    migration.getScriptName(), migration.getVersion()));
            File tempFile = new File(filePath);
            tempFile.delete();
        } catch (Exception exception) {
            exception.printStackTrace();
            logMigration(migration, true, false);
            LOGGER.info("End data migration for data.");
        }
    }


    private void executeStatement(String statement) {
        if (!statement.isEmpty()) {
            SimpleStatement simpleStatement = new SimpleStatement(statement);
            simpleStatement.setConsistencyLevel(ConsistencyLevel.QUORUM);
            session.execute(simpleStatement);
        }
    }

    /**
     * Inserts the result of the migration into the migration table
     *
     * @param migration      the migration that was executed
     * @param wasSuccessful  indicates if the migration was successful or not
     * @param dataSuccessful
     */
    private void logMigration(DbMigration migration, Boolean wasSuccessful, Boolean dataSuccessful) {
        BoundStatement boundStatement = logMigrationStatement.bind(wasSuccessful, migration.getVersion(), dataSuccessful,
                migration.getScriptName(), migration.getMigrationScript(), new Date());
        session.execute(boundStatement);
    }
}
