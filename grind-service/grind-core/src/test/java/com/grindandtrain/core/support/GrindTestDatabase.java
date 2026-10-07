package com.grindandtrain.core.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * A real Postgres 17 for tests, set up like production: Flyway migrates as the owner role {@value #OWNER_ROLE}, and
 * the application connects as {@value #APP_ROLE}, which row-level security applies to. One instance per test JVM.
 * <p>
 * Shared with grind-api and grind-worker through grind-core's test jar.
 *
 * @author Dheeraj_Edupuganti
 */
public final class GrindTestDatabase {

    public static final String OWNER_ROLE = "grind_owner";
    public static final String APP_ROLE = "grind_app";
    public static final String SUPPORT_ROLE = "grind_support";
    private static final String SCHEMA = "grind";

    private static GrindTestDatabase instance;

    private final EmbeddedPostgres postgres;
    private final DataSource appDataSource;

    private GrindTestDatabase() throws IOException, SQLException {
        postgres = EmbeddedPostgres.start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("create role " + OWNER_ROLE + " login");
            statement.execute("create role " + APP_ROLE + " login");
            statement.execute("create role " + SUPPORT_ROLE + " login");
            statement.execute("create schema " + SCHEMA + " authorization " + OWNER_ROLE);
            statement.execute("grant usage on schema " + SCHEMA + " to " + APP_ROLE);
        }
        Flyway.configure()
                .dataSource(dataSource(OWNER_ROLE))
                .schemas(SCHEMA)
                .placeholders(Map.of("app_role", APP_ROLE, "support_role", SUPPORT_ROLE))
                .load()
                .migrate();
        appDataSource = dataSource(APP_ROLE);
    }

    /** Starts the database and applies the migrations on first use. */
    public static synchronized GrindTestDatabase get() {
        if (instance == null) {
            try {
                instance = new GrindTestDatabase();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (SQLException e) {
                throw new IllegalStateException("Could not set up the test database", e);
            }
        }
        return instance;
    }

    /** Connections as the application role: row-level security applies. */
    public DataSource appDataSource() {
        return appDataSource;
    }

    private DataSource dataSource(String role) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl(role, "postgres"));
        dataSource.setCurrentSchema(SCHEMA);
        return dataSource;
    }
}
