package com.fxtradeops.sequenceprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Event Sequence Processor — a Kafka Streams application
 * that detects sequencing anomalies in the trade event stream.
 */
@SpringBootApplication
public class EventSequenceProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventSequenceProcessorApplication.class, args);
    }
}
