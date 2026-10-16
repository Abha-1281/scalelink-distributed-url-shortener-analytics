/**
 * <h1>ScaleLink URL & Redirection Microservice</h1>
 *
 * <h2>HIGH-LEVEL ARCHITECTURE (HLA)</h2>
 * <p>
 * This microservice forms the low-latency core of the ScaleLink distributed platform.
 * It is responsible for creating shortened URLs and resolving short codes into 302 Found redirects
 * with a target SLA of &lt; 5ms at p95.
 * </p>
 *
 * <h2>LOW-LEVEL ARCHITECTURE (LLA) - HEXAGONAL ARCHITECTURE</h2>
 * <p>
 * The code is strictly structured into three decoupled hexagonal concentric rings:
 * </p>
 *
 * <ul>
 *   <li><b>Ring 1: Pure Domain ({@code com.scalelink.urlservice.domain})</b>
 *     <ul>
 *       <li>Contains pure business entities ({@code ShortUrl}, {@code ClickEvent}).</li>
 *       <li>Domain services ({@code SnowflakeIdGenerator}, {@code Base62Encoder}).</li>
 *       <li><b>Architectural Rule:</b> ZERO Spring framework annotations, ZERO database drivers, ZERO external dependencies.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ring 2: Application Layer ({@code com.scalelink.urlservice.application})</b>
 *     <ul>
 *       <li>Inbound Ports (Driving Interfaces): {@code CreateUrlUseCase}, {@code RedirectUrlUseCase}.</li>
 *       <li>Outbound Ports (Driven SPIs): {@code UrlRepositoryPort}, {@code UrlCachePort}, {@code ClickEventPublisherPort}, {@code WorkerIdLeasePort}.</li>
 *       <li>Application Service: {@code UrlApplicationService} orchestrating business operations with TTL jitter.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ring 3: Infrastructure Adapters ({@code com.scalelink.urlservice.infrastructure})</b>
 *     <ul>
 *       <li>Web Adapter: Spring MVC REST Controller handling HTTP 201 creation and HTTP 302 redirection.</li>
 *       <li>Persistence Adapter: Spring Data JPA + Flyway migrations on PostgreSQL {@code link_db}.</li>
 *       <li>Cache Adapter: Redis 7 in-memory cache, Murmur3 Bitmap Bloom Filter, and dynamic worker ID lease.</li>
 *       <li>Messaging Adapter: Asynchronous non-blocking Kafka producer publishing to topic {@code url.clicks.v1}.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>CONCURRENCY &amp; THREADING MODEL</h2>
 * <p>
 * Runs on Java 21 LTS with Virtual Threads (Project Loom) enabled via
 * {@code spring.threads.virtual.enabled: true}, handling tens of thousands of concurrent I/O-bound
 * redirection requests without OS thread exhaustion.
 * </p>
 */
package com.scalelink.urlservice;
