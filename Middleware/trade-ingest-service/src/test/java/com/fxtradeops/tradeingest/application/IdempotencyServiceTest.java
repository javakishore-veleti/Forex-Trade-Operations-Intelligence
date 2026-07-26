package com.fxtradeops.tradeingest.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.tradeingest.api.dto.TradeCaptureResponse;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyEntity;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdempotencyService.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        idempotencyService = new IdempotencyService(redisTemplate, idempotencyKeyRepository, objectMapper, 24);
    }

    @Test
    void check_cacheHit_returnsCachedResponse() throws Exception {
        String key = "test-key-123";
        TradeCaptureResponse expected = new TradeCaptureResponse("FX-000001", "corr-1", "CAPTURED");
        String json = objectMapper.writeValueAsString(expected);

        when(valueOps.get("idempotency:" + key)).thenReturn(json);

        Optional<TradeCaptureResponse> result = idempotencyService.check(key);

        assertThat(result).isPresent();
        assertThat(result.get().tradeId()).isEqualTo("FX-000001");
        assertThat(result.get().status()).isEqualTo("CAPTURED");
    }

    @Test
    void check_cacheMiss_dbHit_returnsDbResponse() {
        String key = "test-key-456";
        when(valueOps.get("idempotency:" + key)).thenReturn(null);

        IdempotencyKeyEntity dbEntity = new IdempotencyKeyEntity(key, "FX-000002", Instant.now());
        when(idempotencyKeyRepository.findById(key)).thenReturn(Optional.of(dbEntity));

        Optional<TradeCaptureResponse> result = idempotencyService.check(key);

        assertThat(result).isPresent();
        assertThat(result.get().tradeId()).isEqualTo("FX-000002");
    }

    @Test
    void check_cacheMiss_dbMiss_returnsEmpty() {
        String key = "test-key-789";
        when(valueOps.get("idempotency:" + key)).thenReturn(null);
        when(idempotencyKeyRepository.findById(key)).thenReturn(Optional.empty());

        Optional<TradeCaptureResponse> result = idempotencyService.check(key);

        assertThat(result).isEmpty();
    }

    @Test
    void mark_setsRedisKeyWithTtl() throws Exception {
        String key = "test-key-mark";
        TradeCaptureResponse response = new TradeCaptureResponse("FX-000003", "corr-3", "CAPTURED");

        idempotencyService.mark(key, response);

        verify(valueOps).set(eq("idempotency:" + key), anyString(), eq(24L), eq(java.util.concurrent.TimeUnit.HOURS));
    }
}
