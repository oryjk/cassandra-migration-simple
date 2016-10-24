package com.betalpha.command;

import com.betalpha.migration.Database;
import com.betalpha.migration.MigrationRepository;
import com.betalpha.migration.MigrationTask;
import com.datastax.driver.core.Cluster;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Created by carlwang on 10/12/16.
 */
@Component
@Slf4j
@Data
public class CassandraMigrationCommandService implements CommandLineRunner {
    @Value("${cassandra.contactpoints}")
    private String hostname;

    @Value("${cassandra.port}")
    private int port;

    @Value("${cassandra.keyspace}")
    private String keyspace;

    @Autowired
    private MigrationRepository migrationRepository;


    @Override
    public void run(String... args) throws Exception {
        Cluster cluster = new Cluster.Builder().addContactPoints(hostname).withPort(port).build();
        Database database = new Database(cluster, keyspace);
        MigrationTask migration = new MigrationTask(database, migrationRepository);
        log.info("default migration folder:" + MigrationRepository.DEFAULT_SCRIPT_PATH);
        migration.migrate();
    }
}
