package com.scalelink.urlservice.infrastructure.adapter.out.messaging;

import com.scalelink.urlservice.application.port.out.ClickEventPublisherPort;
import com.scalelink.urlservice.domain.model.ClickEvent;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Asynchronous Kafka Event Publisher Adapter.
 * Publishes ClickEvent records keyed by shortCode to ensure partition alignment.
 * Executes non-blocking fire-and-forget dispatching to preserve sub-5ms redirect latency SLA.
 * Propagates W3C traceparent context across the message boundary for distributed tracing.
 */
@Slf4j
@Component
public class KafkaClickEventPublisherAdapter implements ClickEventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;
    private final Tracer tracer;

    public KafkaClickEventPublisherAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.clicks:url.clicks.v1}") String topicName,
            @Autowired(required = false) Tracer tracer
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.tracer = tracer;
    }

    @Override
    public void publish(ClickEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                topicName,
                event.shortCode(), // Keyed by shortCode for partition affinity
                event
        );

        // Inject W3C traceparent context into Kafka record header if active span exists
        if (tracer != null && tracer.currentSpan() != null) {
            String traceId = tracer.currentSpan().context().traceId();
            String spanId = tracer.currentSpan().context().spanId();
            String traceparent = String.format("00-%s-%s-01", traceId, spanId);
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        // Non-blocking asynchronous callback
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Async Kafka dispatch failed for event [{}], shortCode [{}]: {}",
                        event.eventId(), event.shortCode(), ex.getMessage());
            } else {
                log.debug("Successfully published click event [{}] to partition [{}] at offset [{}]",
                        event.eventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
