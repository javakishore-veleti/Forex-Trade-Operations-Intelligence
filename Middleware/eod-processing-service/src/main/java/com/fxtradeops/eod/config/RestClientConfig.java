package com.fxtradeops.eod.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * REST client configuration with bounded timeouts for peer service calls (GP-Rq-10).
 */
@Configuration
public class RestClientConfig {

    @Value("${eod.peer.business-calendar.timeout-ms:3000}")
    private int timeoutMs;

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        return RestClient.builder()
                .requestFactory(factory);
    }
}
