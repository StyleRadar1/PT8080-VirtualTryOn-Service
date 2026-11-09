package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración para verificar la ejecución secuencial de migraciones Flyway (V1 -> V2 -> V3 -> V4)
 * sobre PostgreSQL real mediante Testcontainers, garantizando la inmutabilidad y compatibilidad de esquema (SR-RF-24 y SR-RF-25).
 */
class FlywayMigrationPostgresTest {

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("HU SR-RF-24/25: Dado scripts de migración Flyway V1 a V4, Cuando se ejecutan en PostgreSQL (Testcontainers), Entonces aplica todas las migraciones y crea las tablas del Probador Virtual")
    void shouldRunAllFlywayMigrationsSuccessfullyOnPostgreSqlContainer() throws Exception {
        Assumptions.assumeTrue(
                isDockerAvailable(),
                "Docker no se encuentra en ejecución en este host; se omite la prueba de Testcontainers de Flyway"
        );

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("probador_db")
                .withUsername("test_user")
                .withPassword("test_pass")) {

            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            MigrateResult result = flyway.migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(4);

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword());
                 Statement statement = connection.createStatement()) {

                Set<String> tables = new HashSet<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
                    while (rs.next()) {
                        tables.add(rs.getString("table_name").toLowerCase());
                    }
                }

                assertThat(tables).contains(
                        "try_on_jobs",
                        "user_quotas",
                        "biometric_consents",
                        "biometric_photos",
                        "catalog_garments",
                        "outbox_events"
                );

                Set<String> quotaColumns = new HashSet<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'user_quotas'")) {
                    while (rs.next()) {
                        quotaColumns.add(rs.getString("column_name").toLowerCase());
                    }
                }
                assertThat(quotaColumns).contains("is_radar_plus", "created_at", "monthly_limit", "used_count");

                Set<String> garmentColumns = new HashSet<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'catalog_garments'")) {
                    while (rs.next()) {
                        garmentColumns.add(rs.getString("column_name").toLowerCase());
                    }
                }
                assertThat(garmentColumns).contains("garment_id", "name", "category", "image_url", "visible", "updated_at");

                Set<String> tryOnJobColumns = new HashSet<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'try_on_jobs'")) {
                    while (rs.next()) {
                        tryOnJobColumns.add(rs.getString("column_name").toLowerCase());
                    }
                }
                assertThat(tryOnJobColumns).contains("garment_category", "ai_provider", "photo_url", "status");
            }
        }
    }

    @Test
    @DisplayName("HU SR-RF-24/25: Dado los recursos de Flyway en classpath, Cuando se inspecciona su estructura, Entonces V1, V2, V3 y V4 preservan la inmutabilidad de la historia")
    void shouldValidateFlywayScriptIntegrityAndImmutability() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();

        try (InputStream v1 = classLoader.getResourceAsStream("db/migration/V1__init_virtual_try_on_schema.sql");
             InputStream v2 = classLoader.getResourceAsStream("db/migration/V2__create_biometric_consent_and_domain_tables.sql");
             InputStream v3 = classLoader.getResourceAsStream("db/migration/V3__create_catalog_garments_and_update_quotas.sql");
             InputStream v4 = classLoader.getResourceAsStream("db/migration/V4__add_try_on_job_rf25_columns.sql");
             InputStream v5 = classLoader.getResourceAsStream("db/migration/V5__create_outbox_events_table.sql")) {

            assertThat(v1).as("Script V1 debe existir en classpath").isNotNull();
            assertThat(v2).as("Script V2 debe existir en classpath").isNotNull();
            assertThat(v3).as("Script V3 debe existir en classpath").isNotNull();
            assertThat(v4).as("Script V4 debe existir en classpath").isNotNull();
            assertThat(v5).as("Script V5 debe existir en classpath").isNotNull();

            String v2Content = new String(v2.readAllBytes(), StandardCharsets.UTF_8);
            String v3Content = new String(v3.readAllBytes(), StandardCharsets.UTF_8);
            String v4Content = new String(v4.readAllBytes(), StandardCharsets.UTF_8);
            String v5Content = new String(v5.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(v2Content)
                    .as("V2 no debe contener modificaciones posteriores a RF-23")
                    .doesNotContain("catalog_garments")
                    .contains("biometric_consents", "biometric_photos");

            assertThat(v3Content)
                    .as("V3 debe contener los cambios exclusivos de RF-24")
                    .contains("catalog_garments", "is_radar_plus");

            assertThat(v4Content)
                    .as("V4 debe contener la ampliación de columnas para RF-25")
                    .contains("garment_category", "ai_provider");

            assertThat(v5Content)
                    .as("V5 debe contener la creación de outbox_events para Transactional Outbox")
                    .contains("outbox_events", "claimed_at", "claim_expires_at");
        }
    }
}
