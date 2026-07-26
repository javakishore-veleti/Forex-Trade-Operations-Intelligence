package com.fxtradeops.tradeingest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.trade.CurrencyPair;
import com.fxtradeops.domain.trade.TradeDirection;
import com.fxtradeops.tradeingest.api.dto.TradeCaptureResponse;
import com.fxtradeops.tradeingest.api.dto.TradeRequest;
import com.fxtradeops.tradeingest.application.TradeCaptureService;
import com.fxtradeops.tradeingest.config.SecurityConfig;
import com.fxtradeops.tradeingest.web.CorrelationIdFilter;
import com.fxtradeops.tradeingest.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for TradeIngestController using MockMvc.
 */
@WebMvcTest(TradeIngestController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SecurityConfig.class})
class TradeIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeCaptureService tradeCaptureService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
    void happyPath_returns201() throws Exception {
        TradeCaptureResponse response = new TradeCaptureResponse("FX-000001", "test-corr-id", "CAPTURED");
        when(tradeCaptureService.capture(any(), anyString(), anyString()))
                .thenReturn(new TradeCaptureService.CaptureResult(response, false));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-1")
                        .header("X-Correlation-Id", "test-corr-id")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeId").value("FX-000001"))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void idempotencyReplay_returns200() throws Exception {
        TradeCaptureResponse response = new TradeCaptureResponse("FX-000001", "test-corr-id", "CAPTURED");
        when(tradeCaptureService.capture(any(), anyString(), anyString()))
                .thenReturn(new TradeCaptureService.CaptureResult(response, true));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-1")
                        .header("X-Correlation-Id", "test-corr-id")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("FX-000001"));
    }

    @Test
    void missingCurrencyPair_returns400() throws Exception {
        String json = """
                {
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-2")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currencyPair"));
    }

    @Test
    void negativeNotionalAmount_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": -100,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-3")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("notionalAmount"));
    }

    @Test
    void blankNotionalCurrency_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-4")
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingDirection_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-5")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("direction"));
    }

    @Test
    void missingTradeDate_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-6")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tradeDate"));
    }

    @Test
    void missingValueDate_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-7")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("valueDate"));
    }

    @Test
    void blankCounterpartyId_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "",
                    "tradingBookId": "BOOK-APAC-001",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-8")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("counterpartyId"));
    }

    @Test
    void blankTradingBookId_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "",
                    "regionCode": "APAC"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-9")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tradingBookId"));
    }

    @Test
    void missingRegionCode_returns400() throws Exception {
        String json = """
                {
                    "currencyPair": {"baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR"},
                    "notionalAmount": 1500000.00,
                    "notionalCurrency": "USD",
                    "direction": "BUY",
                    "tradeDate": "%s",
                    "valueDate": "%s",
                    "counterpartyId": "CP-AURORA-001",
                    "tradingBookId": "BOOK-APAC-001"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-key-10")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("regionCode"));
    }
}
