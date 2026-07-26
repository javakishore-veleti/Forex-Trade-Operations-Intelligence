package com.fxtradeops.calendar.api.dto;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.Instant;

public record PostCutoffView(RegionCode region, Instant instant, boolean postCutoff) {
}
