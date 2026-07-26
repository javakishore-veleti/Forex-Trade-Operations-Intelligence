package com.fxtradeops.sequenceprocessor;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;

import java.util.Properties;

/**
 * Spring-managed lifecycle for the Kafka Streams instance.
 * Exposes a health indicator that reports DOWN if the stream thread is not running.
 */
@Configuration
@EnableConfigurationProperties(SequenceProcessorConfig.class)
public class KafkaStreamsLifecycle implements SmartLifecycle, HealthIndicator {

    private final SequenceProcessorConfig config;
    private KafkaStreams streams;
    private volatile boolean running = false;

    public KafkaStreamsLifecycle(SequenceProcessorConfig config) {
        this.config = config;
    }

    @Bean
    public Topology sequenceProcessorTopology() {
        SequenceProcessorTopology topologyBuilder = new SequenceProcessorTopology(config);
        return topologyBuilder.buildTopology();
    }

    @Override
    public void start() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, config.applicationId());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams/" + config.applicationId());

        Topology topology = sequenceProcessorTopology();
        streams = new KafkaStreams(topology, props);
        streams.setUncaughtExceptionHandler((thread, exception) -> {
            // Log but allow streams to be restarted
        });
        streams.start();
        running = true;
    }

    @Override
    public void stop() {
        if (streams != null) {
            streams.close();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // Start last, stop first
    }

    @Override
    public Health health() {
        if (streams == null || !running) {
            return Health.down().withDetail("reason", "Kafka Streams not started").build();
        }
        KafkaStreams.State state = streams.state();
        if (state == KafkaStreams.State.RUNNING || state == KafkaStreams.State.REBALANCING) {
            return Health.up()
                    .withDetail("state", state.name())
                    .withDetail("applicationId", config.applicationId())
                    .build();
        }
        return Health.down()
                .withDetail("state", state.name())
                .withDetail("reason", "Stream thread not running or state store not restored")
                .build();
    }

    /**
     * Exposes the underlying KafkaStreams instance for testing.
     */
    public KafkaStreams getStreams() {
        return streams;
    }
}
