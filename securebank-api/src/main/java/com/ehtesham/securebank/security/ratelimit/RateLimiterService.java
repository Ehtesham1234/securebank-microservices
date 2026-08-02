package com.ehtesham.securebank.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/*
 * M1: the audit noted this is in-memory, so limits are per-instance —
 * running more than one replica of securebank-api means each replica
 * enforces its own separate bucket, so the EFFECTIVE limit across the
 * fleet is (configured limit) × (replica count). Kept as-is for now since
 * this only matters once you're actually running multiple instances
 * behind a load balancer. See the commented Redis-backed version at the
 * bottom of this file for when that's true — swap it in by:
 *   1. adding a redis service to docker-compose (e.g. `redis:7-alpine`,
 *      port 6379, no auth needed on an internal-only network)
 *   2. adding the bucket4j-redis + lettuce dependencies (see comment
 *      below for the exact coordinates)
 *   3. deleting the in-memory implementation below and uncommenting the
 *      Redis one — the public method signature (tryConsume(key, capacity,
 *      refillPeriod)) is identical, so nothing calling this class changes.
 */
@Service
public class RateLimiterService {

    // one bucket PER unique key (IP address, or IP+email combo)
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int capacity, Duration refillPeriod) {

        Bucket bucket = buckets.computeIfAbsent(
                key,
                k -> createNewBucket(capacity, refillPeriod));

        return bucket.tryConsume(1);
    }

    private Bucket createNewBucket(int capacity, Duration refillPeriod) {

        Bandwidth limit = Bandwidth.classic(
                capacity,
                io.github.bucket4j.Refill.intervally(capacity, refillPeriod));

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /*
     * ── Redis-backed alternative (uncomment when Redis is available) ──
     *
     * Buckets live in Redis instead of a local HashMap, so every replica
     * of securebank-api shares the same rate-limit state — the fleet
     * enforces one true limit instead of one per instance.
     *
     * pom.xml additions:
     *   <dependency>
     *       <groupId>com.bucket4j</groupId>
     *       <artifactId>bucket4j-redis</artifactId>
     *       <version>8.10.1</version>
     *   </dependency>
     *   <dependency>
     *       <groupId>io.lettuce</groupId>
     *       <artifactId>lettuce-core</artifactId>
     *   </dependency>
     *
     * application-prod.properties addition:
     *   spring.data.redis.host=${REDIS_HOST:redis}
     *   spring.data.redis.port=${REDIS_PORT:6379}
     *
     * docker-compose-microservices.yml addition (no ports: — internal
     * only, same reasoning as C1):
     *   redis:
     *     image: redis:7-alpine
     *     restart: unless-stopped
     *     networks: [default]
     *
     * -----------------------------------------------------------------
     *
     * import io.github.bucket4j.distributed.proxy.ProxyManager;
     * import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
     * import io.lettuce.core.RedisClient;
     * import io.lettuce.core.api.StatefulRedisConnection;
     * import io.lettuce.core.codec.ByteArrayCodec;
     * import io.lettuce.core.codec.RedisCodec;
     * import io.lettuce.core.codec.StringCodec;
     * import jakarta.annotation.PreDestroy;
     * import org.springframework.beans.factory.annotation.Value;
     * import org.springframework.stereotype.Service;
     *
     * import java.time.Duration;
     * import java.util.function.Supplier;
     *
     * @Service
     * public class RateLimiterService {
     *
     *     private final RedisClient redisClient;
     *     private final StatefulRedisConnection<String, byte[]> connection;
     *     private final ProxyManager<String> proxyManager;
     *
     *     public RateLimiterService(
     *             @Value("${spring.data.redis.host}") String redisHost,
     *             @Value("${spring.data.redis.port}") int redisPort) {
     *
     *         this.redisClient = RedisClient.create(
     *                 "redis://" + redisHost + ":" + redisPort);
     *
     *         RedisCodec<String, byte[]> codec =
     *                 RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
     *         this.connection = redisClient.connect(codec);
     *
     *         this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
     *                 .build();
     *     }
     *
     *     public boolean tryConsume(String key, int capacity, Duration refillPeriod) {
     *
     *         Supplier<io.github.bucket4j.BucketConfiguration> configSupplier = () ->
     *                 io.github.bucket4j.BucketConfiguration.builder()
     *                         .addLimit(Bandwidth.classic(
     *                                 capacity,
     *                                 io.github.bucket4j.Refill.intervally(capacity, refillPeriod)))
     *                         .build();
     *
     *         Bucket bucket = proxyManager.builder()
     *                 .build(key, configSupplier);
     *
     *         return bucket.tryConsume(1);
     *     }
     *
     *     @PreDestroy
     *     public void shutdown() {
     *         connection.close();
     *         redisClient.shutdown();
     *     }
     * }
     */
}