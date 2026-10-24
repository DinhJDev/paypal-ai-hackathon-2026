package com.suitefolk.api;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "local"})
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties="spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class RentLedgerMigrationTests extends JwtTestSupport {
    @Autowired DataSource dataSource;

    @Test void upgradingExistingPaymentsPreservesProviderIdsAndBackfillsUnpaidAndCompletedPeriods() throws Exception {
        String schema="ledger_migration_"+UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate();
            try(var connection=dataSource.getConnection();var statement=connection.createStatement()) {
                connection.setSchema(schema);
                UUID owner=UUID.randomUUID(); UUID schedule=UUID.randomUUID(); UUID payment=UUID.randomUUID();
                statement.execute("INSERT INTO owner_access (id,issuer,subject,enabled) VALUES ('"+owner+"','https://issuer.example/','owner',true)");
                statement.execute("INSERT INTO rent_schedule (id,owner_id,tenant_label,amount,frequency,next_due_date,monthly_anchor_day,status,paypal_payment_token_id) VALUES ('"+schedule+"','"+owner+"','Suite 1',1200,'MONTHLY','2026-10-24',24,'ACTIVE','vault-preserved')");
                statement.execute("INSERT INTO rent_payment (id,schedule_id,due_date,amount,status,idempotency_key,paypal_order_id,paypal_capture_id) VALUES ('"+payment+"','"+schedule+"','2026-09-24',1200,'COMPLETED','legacy-request','legacy-order','legacy-capture')");
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
                try(var result=statement.executeQuery("SELECT count(*) FROM rent_period")) { result.next();assertThat(result.getInt(1)).isEqualTo(2); }
                try(var result=statement.executeQuery("SELECT paypal_order_id,paypal_capture_id,status FROM rent_payment")) {
                    result.next();assertThat(result.getString(1)).isEqualTo("legacy-order");
                    assertThat(result.getString(2)).isEqualTo("legacy-capture");assertThat(result.getString(3)).isEqualTo("COMPLETED");
                }
                try(var result=statement.executeQuery("SELECT paypal_payment_token_id FROM rent_schedule")) { result.next();assertThat(result.getString(1)).isEqualTo("vault-preserved"); }
                try(var result=statement.executeQuery("SELECT end_date FROM rent_schedule")) { result.next();assertThat(result.getDate(1)).isNull(); }
                try(var result=statement.executeQuery("SELECT count(*) FROM lease_workspace")) { result.next();assertThat(result.getInt(1)).isZero(); }
            }
        } finally {
            // This UUID-named schema exists only inside the disposable Testcontainers database.
            try(var connection=dataSource.getConnection();var statement=connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
            }
        }
    }
}
