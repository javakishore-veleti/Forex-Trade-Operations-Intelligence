package com.fxtradeops.riskcalc.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Drools configuration: loads versioned DRL rule file from classpath and builds KieContainer at startup.
 */
@Configuration
public class DroolsConfig {

    private static final Logger log = LoggerFactory.getLogger(DroolsConfig.class);
    private static final String RULE_VERSION_MARKER = "// RULE_VERSION:";

    @Bean
    public KieContainer kieContainer(@Value("${risk.rules.package-path}") Resource drlResource) throws IOException {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules/currency-pair-risk.drl",
                ResourceFactory.newInputStreamResource(drlResource.getInputStream()));

        KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll();
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Drools rule compilation errors: " + errors);
        }

        log.info("Drools rules compiled successfully. Rule version: {}", ruleVersion(drlResource));
        return ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    }

    @Bean
    public String ruleVersion(@Value("${risk.rules.package-path}") Resource drlResource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(drlResource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> line.contains(RULE_VERSION_MARKER))
                    .map(line -> line.substring(line.indexOf(RULE_VERSION_MARKER) + RULE_VERSION_MARKER.length()).trim())
                    .findFirst()
                    .orElse("UNKNOWN");
        }
    }
}
