package id.bi.detp.saga;

import id.bi.detp.saga.activity.SagaActivitiesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyIntegrationTest {

    private SagaActivitiesImpl activities;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:h2:mem:saga_" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        var jdbc = new JdbcTemplate(ds);

        try (InputStream in = getClass().getResourceAsStream("/schema.sql")) {
            assertNotNull(in);
            String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : schema.split(";")) {
                if (!stmt.isBlank()) jdbc.execute(stmt);
            }
        }
        activities = new SagaActivitiesImpl(jdbc);
    }

    @Test
    void duplicateUetrReturnsOriginalResponse() {
        String uetr = "11111111-1111-4111-8111-111111111111";
        assertFalse(activities.checkIdempotency(uetr).duplicate());

        activities.applyIssuanceState(uetr, 100_000_000L, "BANK-A");

        var second = activities.checkIdempotency(uetr);
        assertTrue(second.duplicate());
        assertTrue(second.originalStatus().contains("SETTLED"));
    }

    @Test
    void redemptionRecordsIdempotency() {
        String uetr = "22222222-2222-4222-8222-222222222201";
        activities.applyIssuanceState("11111111-1111-4111-8111-111111111101", 200_000_000L, "BANK-A");
        activities.applyRedemptionState(uetr, 50_000_000L, "BANK-A");
        assertTrue(activities.checkIdempotency(uetr).duplicate());
    }
}
