package com.fxtradeops.eod.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configurable close order for regions, bound from application.yml.
 */
@Component
@ConfigurationProperties(prefix = "eod")
public class RegionOrdering {

    private List<String> regionOrder = List.of("APAC", "EMEA", "AMERICAS");
    private int unprocessedTradeTolerance = 0;

    public List<String> getRegionOrder() {
        return regionOrder;
    }

    public void setRegionOrder(List<String> regionOrder) {
        this.regionOrder = regionOrder;
    }

    public int getUnprocessedTradeTolerance() {
        return unprocessedTradeTolerance;
    }

    public void setUnprocessedTradeTolerance(int unprocessedTradeTolerance) {
        this.unprocessedTradeTolerance = unprocessedTradeTolerance;
    }

    public boolean isValidRegion(String region) {
        return regionOrder.contains(region);
    }
}
