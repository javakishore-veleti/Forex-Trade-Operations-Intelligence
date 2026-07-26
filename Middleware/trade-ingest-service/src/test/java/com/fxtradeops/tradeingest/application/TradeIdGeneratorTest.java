package com.fxtradeops.tradeingest.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TradeIdGenerator — format assertion FX-000001 through FX-999999.
 */
@ExtendWith(MockitoExtension.class)
class TradeIdGeneratorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TradeIdGenerator tradeIdGenerator;

    @Test
    void next_returnsFormattedId_withLeadingZeros() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        String tradeId = tradeIdGenerator.next();
        assertThat(tradeId).isEqualTo("FX-000001");
    }

    @Test
    void next_returnsFormattedId_forLargeSequence() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(999999L);
        String tradeId = tradeIdGenerator.next();
        assertThat(tradeId).isEqualTo("FX-999999");
    }

    @Test
    void next_padsToSixDigits() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);
        String tradeId = tradeIdGenerator.next();
        assertThat(tradeId).isEqualTo("FX-000042");
        assertThat(tradeId).matches("^FX-\\d{6}$");
    }

    @Test
    void next_formatsMiddleValue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(123456L);
        String tradeId = tradeIdGenerator.next();
        assertThat(tradeId).isEqualTo("FX-123456");
    }
}
