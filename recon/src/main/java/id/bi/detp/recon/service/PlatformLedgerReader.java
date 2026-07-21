package id.bi.detp.recon.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PlatformLedgerReader {

    private final JdbcTemplate jdbc;

    public PlatformLedgerReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PlatformSnapshot snapshot() {
        String lsn = jdbc.queryForObject("SELECT pg_current_wal_lsn()::text", String.class);

        Long walletSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance), 0) FROM detp.wallet_balances", Long.class);
        long platformLedger = walletSum != null ? walletSum : 0L;

        Long supplyTotal = jdbc.queryForObject(
                """
                SELECT COALESCE(total_supply, 0) FROM detp.supply_ledger
                ORDER BY id DESC LIMIT 1
                """, Long.class);
        if (supplyTotal != null && supplyTotal > 0) {
            platformLedger = supplyTotal;
        }

        long pipelineIn = queryLong(
                "SELECT COALESCE(SUM(amount), 0) FROM detp.saga_instances WHERE status IN ('FUNDED', 'MINTING')");
        long pipelineOut = queryLong(
                "SELECT COALESCE(SUM(amount), 0) FROM detp.saga_instances WHERE status = 'FUNDED'");

        List<String> candidateUetrs = jdbc.queryForList(
                """
                SELECT uetr FROM detp.saga_instances
                WHERE status IN ('FUNDED', 'MINTING')
                ORDER BY updated_at DESC
                """, String.class);

        return new PlatformSnapshot(platformLedger, pipelineIn, pipelineOut, lsn, candidateUetrs);
    }

    private long queryLong(String sql) {
        Long sum = jdbc.queryForObject(sql, Long.class);
        return sum != null ? sum : 0L;
    }

    public record PlatformSnapshot(
            long platformLedger,
            long pipelineIn,
            long pipelineOut,
            String lsn,
            List<String> candidateUetrs
    ) {}
}
