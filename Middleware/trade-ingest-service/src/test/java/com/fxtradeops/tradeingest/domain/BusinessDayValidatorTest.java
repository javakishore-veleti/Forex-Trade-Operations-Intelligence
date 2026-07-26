package com.fxtradeops.tradeingest.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BusinessDayValidator.
 */
class BusinessDayValidatorTest {

    private final BusinessDayValidator validator = new BusinessDayValidator(Set.of());

    @Test
    void tradeDateToday_isValid() {
        LocalDate today = LocalDate.of(2025, 7, 24); // Thursday
        assertThat(validator.isWithinWindow(today, today, 5)).isTrue();
    }

    @Test
    void tradeDate4BusinessDaysAgo_isValid() {
        // Thursday July 24 -> Wednesday July 23 (1) -> Tuesday July 22 (2) -> Monday July 21 (3)
        // -> Friday July 18 (4)
        LocalDate today = LocalDate.of(2025, 7, 24); // Thursday
        LocalDate tradeDate = LocalDate.of(2025, 7, 18); // Friday (4 business days before)
        assertThat(validator.isWithinWindow(tradeDate, today, 5)).isTrue();
    }

    @Test
    void tradeDate6BusinessDaysAgo_isInvalid() {
        // Thursday July 24 back 6 business days: Wed23(1),Tue22(2),Mon21(3),Fri18(4),Thu17(5),Wed16(6)
        LocalDate today = LocalDate.of(2025, 7, 24); // Thursday
        LocalDate tradeDate = LocalDate.of(2025, 7, 16); // Wednesday (6 business days before)
        assertThat(validator.isWithinWindow(tradeDate, today, 5)).isFalse();
    }

    @Test
    void weekendDaysAreSkipped() {
        // Monday July 21 to Monday July 14: 
        // Fri18(1),Thu17(2),Wed16(3),Tue15(4),Mon14(5) = 5 business days
        // Saturday and Sunday are skipped
        LocalDate today = LocalDate.of(2025, 7, 21); // Monday
        LocalDate tradeDate = LocalDate.of(2025, 7, 14); // Monday (5 business days before)
        assertThat(validator.isWithinWindow(tradeDate, today, 5)).isTrue();
    }

    @Test
    void holidayIsSkipped() {
        // If July 23 is a holiday, then counting back from July 24:
        // July 22(1),July 21(2),July 18(3),July 17(4),July 16(5)
        // July 23 would not be counted
        LocalDate holiday = LocalDate.of(2025, 7, 23);
        BusinessDayValidator validatorWithHoliday = new BusinessDayValidator(Set.of(holiday));
        
        LocalDate today = LocalDate.of(2025, 7, 24); // Thursday
        // Without holiday: Wed23(1),Tue22(2),Mon21(3),Fri18(4),Thu17(5),Wed16(6) = 6
        // With holiday: Tue22(1),Mon21(2),Fri18(3),Thu17(4),Wed16(5) = 5
        LocalDate tradeDate = LocalDate.of(2025, 7, 16);
        assertThat(validatorWithHoliday.isWithinWindow(tradeDate, today, 5)).isTrue();
    }

    @Test
    void futureTradeDate_isValid() {
        LocalDate today = LocalDate.of(2025, 7, 24);
        LocalDate tradeDate = LocalDate.of(2025, 7, 25);
        assertThat(validator.isWithinWindow(tradeDate, today, 5)).isTrue();
    }

    @Test
    void nullTradeDate_isInvalid() {
        LocalDate today = LocalDate.of(2025, 7, 24);
        assertThat(validator.isWithinWindow(null, today, 5)).isFalse();
    }
}
