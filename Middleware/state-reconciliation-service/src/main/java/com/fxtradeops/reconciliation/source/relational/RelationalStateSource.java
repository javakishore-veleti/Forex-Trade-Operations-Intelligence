package com.fxtradeops.reconciliation.source.relational;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.source.ObservedStateSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Read-only adapter for PostgreSQL trade_current_state table.
 */
@Component
public class RelationalStateSource implements ObservedStateSource {

    private static final Logger log = LoggerFactory.getLogger(RelationalStateSource.class);

    private final JdbcTemplate jdbcTemplate;

    public RelationalStateSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SourceId sourceId() {
        return SourceId.RELATIONAL;
    }

    @Override
    @Transactional(readOnly = true)
    public ObservedState read(String tradeId) {
        try {
            var results = jdbcTemplate.query(
                    "SELECT status, updated_at FROM trade_current_state WHERE trade_id = ?",
                    (ResultSet rs, int rowNum) -> new ObservedState(
                            SourceId.RELATIONAL,
                            TradeStatus.valueOf(rs.getString("status")),
                            toInstant(rs.getTimestamp("updated_at")),
                            true
                    ),
                    tradeId
            );
            if (results.isEmpty()) {
                return ObservedState.unavailable(SourceId.RELATIONAL);
            }
            return results.get(0);
        } catch (Exception e) {
            log.warn("[{}] Failed to read RELATIONAL source for trade {}: {}",
                    MDC.get("correlationId"), tradeId, e.getMessage());
            return ObservedState.unavailable(SourceId.RELATIONAL);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
