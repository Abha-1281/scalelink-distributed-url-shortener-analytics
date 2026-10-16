package com.scalelink.urlservice.infrastructure.config;

import com.scalelink.urlservice.application.port.out.ClickEventPublisherPort;
import com.scalelink.urlservice.application.port.out.UrlCachePort;
import com.scalelink.urlservice.application.port.out.UrlRepositoryPort;
import com.scalelink.urlservice.application.port.out.WorkerIdLeasePort;
import com.scalelink.urlservice.application.service.UrlApplicationService;
import com.scalelink.urlservice.domain.service.Base62Encoder;
import com.scalelink.urlservice.domain.service.SnowflakeIdGenerator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableScheduling
public class AppConfig {

    private long assignedWorkerId = 0L;
    private final WorkerIdLeasePort workerIdLeasePort;

    public AppConfig(WorkerIdLeasePort workerIdLeasePort) {
        this.workerIdLeasePort = workerIdLeasePort;
    }

    @Bean
    public Base62Encoder base62Encoder() {
        return new Base62Encoder();
    }

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        this.assignedWorkerId = workerIdLeasePort.acquireWorkerId();
        log.info("Initialized SnowflakeIdGenerator with dynamic worker ID: {}", assignedWorkerId);
        return new SnowflakeIdGenerator(assignedWorkerId);
    }

    @Scheduled(fixedRate = 10000) // Renew Redis heartbeat every 10 seconds
    public void renewWorkerLease() {
        workerIdLeasePort.renewLease(assignedWorkerId);
    }

    @PreDestroy
    public void releaseWorkerLeaseOnShutdown() {
        workerIdLeasePort.releaseLease(assignedWorkerId);
    }

    @Bean
    public UrlApplicationService urlApplicationService(
            SnowflakeIdGenerator snowflakeIdGenerator,
            Base62Encoder base62Encoder,
            UrlRepositoryPort urlRepositoryPort,
            UrlCachePort urlCachePort,
            ClickEventPublisherPort clickEventPublisherPort
    ) {
        return new UrlApplicationService(
                snowflakeIdGenerator,
                base62Encoder,
                urlRepositoryPort,
                urlCachePort,
                clickEventPublisherPort
        );
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Fast ACK for low-latency non-blocking production
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}
