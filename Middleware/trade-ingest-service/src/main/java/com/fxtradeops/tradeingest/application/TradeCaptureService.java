package com.fxtradeops.tradeingest.application;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.tradeingest.api.dto.TradeCaptureResponse;
import com.fxtradeops.tradeingest.api.dto.TradeRequest;
import com.fxtradeops.tradeingest.domain.BusinessDayValidator;
import com.fxtradeops.tradeingest.domain.DomainValidationException;
import com.fxtradeops.tradeingest.persistence.CapturedTradeEntity;
import com.fxtradeops.tradeingest.persistence.CapturedTradeRepository;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyEntity;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Orchestrates trade capture: idempotency check → domain validation → ID generation →
 * transactional persist + Kafka publish → Redis mark.
 */
@Service
public class TradeCaptureService {

    private static final Logger log = LoggerFactory.getLogger(TradeCaptureService.class);

    private final IdempotencyService idempotencyService;
    private final BusinessDayValidator businessDayValidator;
    private final TradeIdGenerator tradeIdGenerator;
    private final CapturedTradeRepository capturedTradeRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final KafkaTemplate<String, TradeEvent> kafkaTemplate;
    private final String kafkaTopic;
    private final int maxBusinessDays;
    private final Counter tradesCapturedCounter;
    private final MeterRegistry meterRegistry;

    public TradeCaptureService(
            IdempotencyService idempotencyService,
            BusinessDayValidator businessDayValidator,
            TradeIdGenerator tradeIdGenerator,
            CapturedTradeRepository capturedTradeRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            KafkaTemplate<String, TradeEvent> kafkaTemplate,
            @Value("${app.kafka.topic:fxops.trade.events}") String kafkaTopic,
            @Value("${app.validation.max-business-days:5}") int maxBusinessDays,
            MeterRegistry meterRegistry) {
        this.idempotencyService = idempotencyService;
        this.businessDayValidator = businessDayValidator;
        this.tradeIdGenerator = tradeIdGenerator;
        this.capturedTradeRepository = capturedTradeRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
        this.maxBusinessDays = maxBusinessDays;
        this.meterRegistry = meterRegistry;
        this.tradesCapturedCounter = Counter.builder("trades_captured_total")
                .description("Total number of trades successfully captured")
                .register(meterRegistry);
    }

    /**
     * Main capture orchestration method.
     *
     * @param request        the trade request
     * @param idempotencyKey the X-Idempotency-Key header value
     * @param correlationId  the resolved correlation ID
     * @return the capture response (201 new or 200 replay)
     */
    public CaptureResult capture(TradeRequest request, String idempotencyKey, String correlationId) {
        // 1. Idempotency check
        Optional<TradeCaptureResponse> cached = idempotencyService.check(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotency replay for key: {}", idempotencyKey);
            TradeCaptureResponse original = cached.get();
            // Return with the current correlation ID
            return new CaptureResult(
                    new TradeCaptureResponse(original.tradeId(), correlationId, original.status()),
                    true);
        }

        // 2. Domain validation
        validateDomainRules(request);

        // 3. Generate trade ID + persist + publish (transactional)
        String tradeId = tradeIdGenerator.next();
        TradeCaptureResponse response = executeCaptureTransaction(request, tradeId, idempotencyKey, correlationId);

        // 4. Mark idempotency in Redis (after commit)
        idempotencyService.mark(idempotencyKey, response);

        // 5. Increment counter
        tradesCapturedCounter.increment();

        return new CaptureResult(response, false);
    }

    @Transactional
    public TradeCaptureResponse executeCaptureTransaction(
            TradeRequest request, String tradeId, String idempotencyKey, String correlationId) {

        // Persist captured trade entity
        CapturedTradeEntity entity = new CapturedTradeEntity();
        entity.setTradeId(tradeId);
        entity.setCorrelationId(correlationId);
        entity.setCurrencyPairCode(request.currencyPair().pairCode());
        entity.setBaseCurrency(request.currencyPair().baseCurrency());
        entity.setQuoteCurrency(request.currencyPair().quoteCurrency());
        entity.setNotionalAmount(request.notionalAmount());
        entity.setNotionalCurrency(request.notionalCurrency());
        entity.setDirection(request.direction().name());
        entity.setTradeDate(request.tradeDate());
        entity.setValueDate(request.valueDate());
        entity.setCounterpartyId(request.counterpartyId());
        entity.setTradingBookId(request.tradingBookId());
        entity.setRegionCode(request.regionCode().name());
        entity.setStatus(TradeStatus.CAPTURED.name());
        entity.setCreatedAt(Instant.now());
        capturedTradeRepository.save(entity);

        // Persist idempotency key
        IdempotencyKeyEntity keyEntity = new IdempotencyKeyEntity(
                idempotencyKey, tradeId, Instant.now());
        idempotencyKeyRepository.save(keyEntity);

        // Publish TradeCaptured event to Kafka within Kafka transaction
        TradeEvent event = buildTradeEvent(request, tradeId, correlationId);
        kafkaTemplate.executeInTransaction(ops -> {
            ops.send(kafkaTopic, tradeId, event);
            return null;
        });

        return new TradeCaptureResponse(tradeId, correlationId, TradeStatus.CAPTURED.name());
    }

    private void validateDomainRules(TradeRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();

        // Check trade date is within business day window
        if (request.tradeDate() != null &&
                !businessDayValidator.isWithinWindow(request.tradeDate(), LocalDate.now(), maxBusinessDays)) {
            errors.add(Map.of("field", "tradeDate",
                    "message", "must be within " + maxBusinessDays + " business days of today"));
            incrementValidationFailureCounter("tradeDate");
        }

        // Check value date is strictly after trade date
        if (request.tradeDate() != null && request.valueDate() != null &&
                !request.valueDate().isAfter(request.tradeDate())) {
            errors.add(Map.of("field", "valueDate",
                    "message", "must be strictly after tradeDate"));
            incrementValidationFailureCounter("valueDate");
        }

        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    private void incrementValidationFailureCounter(String field) {
        Counter.builder("trade_validation_failures_total")
                .tag("field", field)
                .description("Total number of trade validation failures")
                .register(meterRegistry)
                .increment();
    }

    private TradeEvent buildTradeEvent(TradeRequest request, String tradeId, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyPairCode", request.currencyPair().pairCode());
        payload.put("baseCurrency", request.currencyPair().baseCurrency());
        payload.put("quoteCurrency", request.currencyPair().quoteCurrency());
        payload.put("notionalAmount", request.notionalAmount());
        payload.put("notionalCurrency", request.notionalCurrency());
        payload.put("direction", request.direction().name());
        payload.put("tradeDate", request.tradeDate().toString());
        payload.put("valueDate", request.valueDate().toString());
        payload.put("counterpartyId", request.counterpartyId());
        payload.put("tradingBookId", request.tradingBookId());
        payload.put("regionCode", request.regionCode().name());
        payload.put("status", TradeStatus.CAPTURED.name());

        return new TradeEvent(
                UUID.randomUUID().toString(),
                tradeId,
                correlationId,
                TradeEventType.TRADE_CAPTURED,
                Instant.now(),
                1L,
                "trade-ingest-service",
                payload
        );
    }

    /**
     * Result wrapper that indicates whether this was a new capture or a replay.
     */
    public record CaptureResult(TradeCaptureResponse response, boolean replay) {
    }
}
