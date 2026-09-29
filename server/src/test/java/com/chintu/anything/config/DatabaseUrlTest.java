package com.chintu.anything.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void takesANeonStringAsPasted() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgresql://neondb_owner:npg_AbC123@ep-quiet-sun-a1b2c3.ap-southeast-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require");

        assertThat(url.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-quiet-sun-a1b2c3.ap-southeast-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channelBinding=require");
        assertThat(url.username()).isEqualTo("neondb_owner");
        assertThat(url.password()).isEqualTo("npg_AbC123");
    }

    @Test
    void acceptsTheShortSchemeAndAPort() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://app:pw@db.example.com:6543/anything");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:6543/anything?sslmode=require");
    }

    @Test
    void acceptsAJdbcUrlToo() {
        DatabaseUrl url = DatabaseUrl.parse("jdbc:postgresql://app:pw@db.example.com/anything?sslmode=verify-full");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com/anything?sslmode=verify-full");
        assertThat(url.username()).isEqualTo("app");
    }

    @Test
    void decodesAPasswordWithSpecialCharacters() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://app:p%40ss+w%2Frd@db.example.com/anything");

        assertThat(url.password()).isEqualTo("p@ss+w/rd");
    }

    @Test
    void neverTalksToARemoteDatabaseWithoutEncryption() {
        assertThat(DatabaseUrl.parse("postgresql://a:b@db.example.com/x").jdbcUrl()).endsWith("?sslmode=require");
        assertThat(DatabaseUrl.parse("postgresql://a:b@localhost:5433/x").jdbcUrl())
                .isEqualTo("jdbc:postgresql://localhost:5433/x");
    }

    @Test
    void thePooledNeonEndpointTurnsOffServerSidePreparedStatements() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgresql://a:b@ep-quiet-sun-a1b2c3-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require");

        assertThat(url.jdbcUrl()).endsWith("?sslmode=require&prepareThreshold=0");
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(DatabaseUrl.parse("postgresql://app:hunter2@db.example.com/anything").toString())
                .doesNotContain("hunter2");
    }

    @Test
    void explainsWhatIsWrong() {
        assertThatThrownBy(() -> DatabaseUrl.parse(null)).hasMessageContaining("not set");
        assertThatThrownBy(() -> DatabaseUrl.parse("  ")).hasMessageContaining("not set");
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://a:b@host/db")).hasMessageContaining("postgresql://");
        assertThatThrownBy(() -> DatabaseUrl.parse("postgresql://a:b@host")).hasMessageContaining("database name");
    }
}
