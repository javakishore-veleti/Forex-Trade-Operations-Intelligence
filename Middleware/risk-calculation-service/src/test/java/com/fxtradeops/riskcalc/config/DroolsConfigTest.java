package com.fxtradeops.riskcalc.config;

import com.fxtradeops.riskcalc.domain.RiskFact;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.io.ResourceFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Drools configuration: compilation, rule version parsing, session execution.
 */
class DroolsConfigTest {

    @Test
    void kieContainerBuildsWithNoErrors() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        InputStream drl = getClass().getResourceAsStream("/rules/currency-pair-risk.drl");
        assertNotNull(drl, "DRL file should be on classpath");

        kfs.write("src/main/resources/rules/currency-pair-risk.drl",
                ResourceFactory.newInputStreamResource(drl));
        KieBuilder builder = ks.newKieBuilder(kfs).buildAll();

        List<Message> errors = builder.getResults().getMessages(Message.Level.ERROR);
        assertTrue(errors.isEmpty(), "DRL should compile without errors: " + errors);
    }

    @Test
    void ruleVersionParses() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rules/currency-pair-risk.drl"),
                StandardCharsets.UTF_8))) {
            String version = reader.lines()
                    .filter(line -> line.contains("// RULE_VERSION:"))
                    .map(line -> line.substring(line.indexOf("// RULE_VERSION:") + "// RULE_VERSION:".length()).trim())
                    .findFirst()
                    .orElse(null);

            assertNotNull(version);
            assertEquals("RULES-7.14", version);
        }
    }

    @Test
    void statelessKieSessionFiresExpectedPairRule() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        InputStream drl = getClass().getResourceAsStream("/rules/currency-pair-risk.drl");
        kfs.write("src/main/resources/rules/currency-pair-risk.drl",
                ResourceFactory.newInputStreamResource(drl));
        ks.newKieBuilder(kfs).buildAll();
        KieContainer container = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());

        StatelessKieSession session = container.newStatelessKieSession();
        RiskFact fact = new RiskFact("FX-000001", "EURUSD", "EUR", "USD",
                new BigDecimal("1000000"), "USD", "EMEA", "FX-BOOK-001");

        session.execute(Collections.singletonList(fact));

        assertTrue(fact.getRulesFired().contains("FX-PAIR-EURUSD-001"),
                "EURUSD rule should fire");
        assertTrue(fact.getRiskAmount().compareTo(BigDecimal.ZERO) > 0,
                "Risk amount should be set");
    }
}
