package com.fxtradeops.tradeingest.application;

import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.trade.CurrencyPair;
import com.fxtradeops.domain.trade.TradeDirection;
import com.fxtradeops.tradeingest.api.dto.TradeCaptureResponse;
import com.fxtradeops.tradeingest.api.dto.TradeRequest;
import com.fxtradeops.tradeingest.domain.BusinessDayValidator;
import com.fxtradeops.tradeingest.domain.DomainValidationException;
import com.fxtradeops.tradeingest.persistence.CapturedTradeRepository;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyRepository;
import com.fxtradeops.domain.event.TradeEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TradeCaptureService.
 */
@ExtendWith(MockitoExtension.class)
class TradeCaptureServiceTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TradeIdGenerator tradeIdGenerator;

    @Mock
    private CapturedTradeRepository capturedTradeRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private KafkaTemplate<String, TradeEvent> kafkaTemplate;

    private TradeCaptureService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        BusinessDayValidator validator = new BusinessDayValidator();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        // Mock executeInTransaction to actually execute the callback
        lenient().when(kafkaTemplate.executeInTransaction(any())).thenAnswer(invocation -> {
            var callback = (org.springframework.kafka.core.KafkaOperations.OperationsCallback<String, TradeEvent, Object>) invocation.getArgument(0);
            return callback.doInOperations(kafkaTemplate);
        });
        service = new TradeCaptureService(
                idempotencyService, validator, tradeIdGenerator,
                capturedTradeRepository, idempotencyKeyRepository,
                kafkaTemplate, "fxops.trade.events", 5, meterRegistry);
    }

    private TradeRequest validRequest() {
        return new TradeRequest(
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1500000.00"),
                "USD",
                TradeDirection.BUY,
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                "CP-AURORA-001",
                "BOOK-APAC-001",
                RegionCode.APAC
        );
    }

    @Test
    void capture_validRequest_producesEntityAndEvent() {
        when(idempotencyService.check(any())).thenReturn(Optional.empty());
        when(tradeIdGenerator.next()).thenReturn("FX-000001");
        when(capturedTradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeCaptureService.CaptureResult result = service.capture(validRequest(), "key-1", "corr-1");

        assertThat(result.replay()).isFalse();
        assertThat(result.response().tradeId()).isEqualTo("FX-000001");
        assertThat(result.response().status()).isEqualTo("CAPTURED");
        verify(capturedTradeRepository).save(any());
        verify(kafkaTemplate).send(eq("fxops.trade.events"), eq("FX-000001"), any(TradeEvent.class));
    }

    @Test
    void capture_idempotencyReplay_returnsSameTradeId() {
        TradeCaptureResponse cached = new TradeCaptureResponse("FX-000001", "corr-old", "CAPTURED");
        when(idempotencyService.check("key-replay")).thenReturn(Optional.of(cached));

        TradeCaptureService.CaptureResult result = service.capture(validRequest(), "key-replay", "corr-new");

        assertThat(result.replay()).isTrue();
        assertThat(result.response().tradeId()).isEqualTo("FX-000001");
        // No DB call should be made
        verify(capturedTradeRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void capture_tradeDateTooOld_throwsDomainValidation() {
        when(idempotencyService.check(any())).thenReturn(Optional.empty());

        TradeRequest badRequest = new TradeRequest(
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1500000.00"),
                "USD",
                TradeDirection.BUY,
                LocalDate.now().minusDays(20), // way too far in the past
                LocalDate.now().plusDays(2),
                "CP-AURORA-001",
                "BOOK-APAC-001",
                RegionCode.APAC
        );

        assertThatThrownBy(() -> service.capture(badRequest, "key-old", "corr-1"))
                .isInstanceOf(DomainValidationException.class);
        verify(capturedTradeRepository, never()).save(any());
    }

    @Test
    void capture_valueDateNotAfterTradeDate_throwsDomainValidation() {
        when(idempotencyService.check(any())).thenReturn(Optional.empty());

        TradeRequest badRequest = new TradeRequest(
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1500000.00"),
                "USD",
                TradeDirection.BUY,
                LocalDate.now(),
                LocalDate.now(), // same as tradeDate, not strictly after
                "CP-AURORA-001",
                "BOOK-APAC-001",
                RegionCode.APAC
        );

        assertThatThrownBy(() -> service.capture(badRequest, "key-vd", "corr-1"))
                .isInstanceOf(DomainValidationException.class);
        verify(capturedTradeRepository, never()).save(any());
    }

    @Test
    void capture_valueDateBeforeTradeDate_throwsDomainValidation() {
        when(idempotencyService.check(any())).thenReturn(Optional.empty());

        TradeRequest badRequest = new TradeRequest(
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1500000.00"),
                "USD",
                TradeDirection.BUY,
                LocalDate.now(),
                LocalDate.now().minusDays(1), // before tradeDate
                "CP-AURORA-001",
                "BOOK-APAC-001",
                RegionCode.APAC
        );

        assertThatThrownBy(() -> service.capture(badRequest, "key-vd2", "corr-1"))
                .isInstanceOf(DomainValidationException.class);
        verify(capturedTradeRepository, never()).save(any());
    }
}
