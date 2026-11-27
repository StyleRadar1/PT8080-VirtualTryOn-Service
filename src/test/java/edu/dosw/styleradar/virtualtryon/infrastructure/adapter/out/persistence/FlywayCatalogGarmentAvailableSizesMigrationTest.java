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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de verificación de la migración V9 (SR-RF-24):
 * Comprueba el paso desde una base de datos que ya tenga aplicada la V3 anterior y una prenda guardada.
 * Verifica que el servicio arranca / migra exitosamente, la columna available_sizes existe y la prenda se conserva.
 */
@DisplayName("Pruebas de Migración Flyway: Evolución catalog_garments de V3 a V9 con prendas existentes")
class FlywayCatalogGarmentAvailableSizesMigrationTest {

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("Dado V3 con prendas guardadas, Cuando se ejecuta DDL de V9 en H2/PostgreSQL, Entonces la columna available_sizes existe y la prenda se conserva")
    void shouldPreserveExistingGarmentWhenApplyingV9AlterTable() throws Exception {
        String dbUrl = "jdbc:h2:mem:v3_to_v9_schema_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        String user = "sa";
        String pass = "";

        // 1. Crear la tabla catalog_garments con el esquema exacto de V3
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS catalog_garments");
            stmt.execute("CREATE TABLE catalog_garments (" +
                    "garment_id UUID PRIMARY KEY, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "category VARCHAR(50) NOT NULL, " +
                    "image_url VARCHAR(1000) NOT NULL, " +
                    "visible BOOLEAN NOT NULL DEFAULT TRUE, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()" +
                    ")");
        }

        UUID existingGarmentId = UUID.randomUUID();
        String originalName = "Chaqueta Cuero Clásica";
        String originalCategory = "TOP";
        String originalImageUrl = "https://storage/cuero.png";
        Instant originalUpdatedAt = Instant.now();

        // 2. Insertar una prenda existente usando el esquema V3
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO catalog_garments (garment_id, name, category, image_url, visible, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, existingGarmentId);
            ps.setString(2, originalName);
            ps.setString(3, originalCategory);
            ps.setString(4, originalImageUrl);
            ps.setBoolean(5, true);
            ps.setTimestamp(6, Timestamp.from(originalUpdatedAt));
            int inserted = ps.executeUpdate();
            assertThat(inserted).isEqualTo(1);
        }

        // 3. Ejecutar la sentencia de migración V9
        ClassLoader classLoader = getClass().getClassLoader();
        String v9Script;
        try (InputStream is = classLoader.getResourceAsStream("db/migration/V9__add_available_sizes_to_catalog_garments.sql")) {
            assertThat(is).isNotNull();
            v9Script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute(v9Script);
        }

        // 4. Verificar que la prenda existente se conserva intacta y que la nueva columna available_sizes es null
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT garment_id, name, category, image_url, visible, available_sizes FROM catalog_garments WHERE garment_id = ?")) {
            ps.setObject(1, existingGarmentId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("garment_id", UUID.class)).isEqualTo(existingGarmentId);
                assertThat(rs.getString("name")).isEqualTo(originalName);
                assertThat(rs.getString("category")).isEqualTo(originalCategory);
                assertThat(rs.getString("image_url")).isEqualTo(originalImageUrl);
                assertThat(rs.getBoolean("visible")).isTrue();
                assertThat(rs.getString("available_sizes")).isNull();
            }
        }

        // 5. Verificar que se pueden actualizar las tallas disponibles
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE catalog_garments SET available_sizes = ? WHERE garment_id = ?")) {
            ps.setString(1, "S,M,L");
            ps.setObject(2, existingGarmentId);
            int updated = ps.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT available_sizes FROM catalog_garments WHERE garment_id = ?")) {
            ps.setObject(1, existingGarmentId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("available_sizes")).isEqualTo("S,M,L");
            }
        }
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado migración real Flyway hasta V3 en PostgreSQL, Cuando se inserta prenda y se migra a V9, Entonces Flyway completa y preserva datos")
    void shouldRunFlywayMigrationV3ToV9OnPostgresContainer() throws Exception {
        Assumptions.assumeTrue(
                isDockerAvailable(),
                "Docker no se encuentra en ejecución en este host; se omite la prueba de Testcontainers de Flyway"
        );

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("probador_db")
                .withUsername("test_user")
                .withPassword("test_pass")) {

            postgres.start();

            // 1. Ejecutar Flyway hasta versión 3
            Flyway flywayV3 = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target("3")
                    .load();

            MigrateResult resultV3 = flywayV3.migrate();
            assertThat(resultV3.success).isTrue();

            UUID existingGarmentId = UUID.randomUUID();
            String originalName = "Camisa Lino Beige";

            // 2. Insertar prenda en esquema V3
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO catalog_garments (garment_id, name, category, image_url, visible, updated_at) " +
                                 "VALUES (?, ?, ?, ?, ?, NOW())")) {
                ps.setObject(1, existingGarmentId);
                ps.setString(2, originalName);
                ps.setString(3, "TOP");
                ps.setString(4, "https://storage/lino.png");
                ps.setBoolean(5, true);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }

            // 3. Ejecutar Flyway hasta la última versión (incluyendo V9)
            Flyway flywayLatest = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            MigrateResult resultLatest = flywayLatest.migrate();
            assertThat(resultLatest.success).isTrue();

            // 4. Verificar existencia de la columna y preservación del registro
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT garment_id, name, available_sizes FROM catalog_garments WHERE garment_id = ?")) {
                ps.setObject(1, existingGarmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isEqualTo(originalName);
                    assertThat(rs.getString("available_sizes")).isNull();
                }
            }
        }
    }
}
