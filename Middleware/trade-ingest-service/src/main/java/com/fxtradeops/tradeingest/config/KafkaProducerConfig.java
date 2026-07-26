package com.fxtradeops.tradeingest.config;

import com.fxtradeops.domain.event.TradeEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration with transactional support.
 * Transaction ID prefix: trade-ingest-
 * Topic: fxops.trade.events (configurable)
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.transaction-id-prefix:trade-ingest-}")
    private String transactionIdPrefix;

    @Bean
    public ProducerFactory<String, TradeEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        DefaultKafkaProducerFactory<String, TradeEvent> factory =
                new DefaultKafkaProducerFactory<>(configProps);
        factory.setTransactionIdPrefix(transactionIdPrefix);
        return factory;
    }

    @Bean
    public KafkaTemplate<String, TradeEvent> kafkaTemplate(
            ProducerFactory<String, TradeEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
