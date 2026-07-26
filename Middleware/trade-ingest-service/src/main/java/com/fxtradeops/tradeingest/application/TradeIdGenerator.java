package com.fxtradeops.tradeingest.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Generates trade IDs of the form FX-NNNNNN using a PostgreSQL sequence.
 */
@Component
public class TradeIdGenerator {

    private final JdbcTemplate jdbcTemplate;

    public TradeIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates the next trade ID from the PostgreSQL sequence.
     *
     * @return trade ID in the format FX-000001 through FX-999999
     */
    public String next() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('trade_id_seq')", Long.class);
        return String.format("FX-%06d", seq);
    }
}
