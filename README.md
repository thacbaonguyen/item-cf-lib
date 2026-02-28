# ItemCF — Item-based Collaborative Filtering Library for Java

A pure Java library that implements Item-based Collaborative Filtering (ItemCF) with a pluggable similarity strategy. Built to be embedded in any JVM application without requiring Spring, JPA, or any other framework.

---

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Configuration Reference](#configuration-reference)
- [Implementing the Ports](#implementing-the-ports)
  - [InteractionLoader](#interactionloader)
  - [SimilarityStore](#similaritystore)
  - [CachePort (optional)](#cacheport-optional)
- [Querying the Engine](#querying-the-engine)
- [Extending with Custom Similarity Strategies](#extending-with-custom-similarity-strategies)
- [Running the Calculation](#running-the-calculation)
- [Integration with Spring Boot](#integration-with-spring-boot)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Item-based Collaborative Filtering is a recommendation technique that identifies items similar to those a user has already interacted with, and surfaces them as recommendations. The algorithm works by analyzing patterns of user interaction across a catalog of items: items that are consistently interacted with by the same users are considered similar.

This library extracts the pure algorithmic logic from a Spring Boot application and repackages it as a standalone artifact. The goal is to allow any Java project, regardless of framework, to integrate a production-quality recommendation engine without copying code or taking on framework dependencies.

Key properties of this library:

- Zero runtime dependency on Spring Framework, JPA, Hibernate, or Redis
- Single compile-time dependency: SLF4J API for logging
- Follows Hexagonal Architecture: all data access is delegated to caller-provided adapters
- The similarity algorithm is pluggable via a strategy interface
- Ships with in-memory implementations of all ports for testing and development
- Fully compatible with Java 17 and later

---

## How It Works

The recommendation pipeline runs in two phases.

**Offline Phase (calculateAndStoreSimilarities)**

This phase is intended to run on a schedule (e.g., nightly). It:

1. Loads all user-item interactions from your data source in batches via `InteractionLoader`
2. Builds a sparse item-user matrix where each item is represented as a vector of user scores
3. Computes the cosine similarity (or any configured strategy) for all unique item pairs
4. Filters out pairs below the configured threshold or with too few common users
5. Persists the qualifying pairs to your store via `SimilarityStore`
6. Evicts any cached recommendation results

**Online Phase (query)**

At request time, the engine answers two types of queries:

- `getSimilarItems(itemId, limit)` — finds items most similar to a given item
- `getRecommendationsForUser(userId, userHistory, limit)` — aggregates similarity scores across all items a user has interacted with and returns ranked candidates the user has not seen

---

## Requirements

- Java 17 or later
- Maven 3.6 or later (or Gradle equivalent)
- No additional runtime dependencies

---

## Installation

The library is installed to your local Maven repository. From the project root:

```bash
mvn clean install
```

Then declare the dependency in your consuming project:

```xml
<dependency>
  <groupId>io.github.thacbaonguyen</groupId>
  <artifactId>itemcf</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## Quick Start

The following example uses the built-in `InMemorySimilarityStore` for demonstration. In a real application you would replace this with a database-backed adapter.

```java
import io.github.thacbao.itemcf.api.RecommendationEngine;
import io.github.thacbao.itemcf.config.RecommendationConfig;
import io.github.thacbao.itemcf.model.Interaction;
import io.github.thacbao.itemcf.model.RecommendationResult;
import io.github.thacbao.itemcf.port.impl.InMemorySimilarityStore;

import java.util.List;
import java.util.Set;

public class QuickStartExample {

    public static void main(String[] args) {
        // 1. Provide interactions (normally from a database)
        List<Interaction> interactions = List.of(
            new Interaction(1, 101, 5.0),  // user 1 bought product 101
            new Interaction(1, 102, 4.0),  // user 1 bought product 102
            new Interaction(2, 101, 5.0),  // user 2 bought product 101
            new Interaction(2, 103, 3.0),  // user 2 bought product 103
            new Interaction(3, 102, 4.0),  // user 3 bought product 102
            new Interaction(3, 103, 4.0)   // user 3 bought product 103
        );

        // 2. Configure the engine
        RecommendationConfig config = RecommendationConfig.builder()
            .similarityThreshold(0.10)
            .minCommonUsers(2)
            .topKSimilar(20)
            .batchSize(100)
            .build();

        InMemorySimilarityStore store = new InMemorySimilarityStore();

        RecommendationEngine engine = new RecommendationEngine(
            (offset, limit) -> offset == 0 ? interactions : List.of(),
            store,
            config
        );

        // 3. Run the offline calculation
        engine.calculateAndStoreSimilarities();

        // 4. Query similar items for product 101
        List<RecommendationResult> similar = engine.getSimilarItems(101, 5);
        similar.forEach(r -> System.out.printf("Item %d, score=%.4f%n", r.itemId(), r.score()));

        // 5. Get personalized recommendations for a user who interacted with item 101
        List<RecommendationResult> forUser = engine.getRecommendationsForUser(
            4, Set.of(101), 10
        );
        forUser.forEach(r -> System.out.printf("Recommended item %d, score=%.4f%n", r.itemId(), r.score()));
    }
}
```

---

## Architecture

The library follows **Hexagonal Architecture** (also known as Ports and Adapters). The core algorithm has no knowledge of how data is stored or retrieved. Instead, the library defines a set of interfaces (ports) that the caller must implement in order to connect the library to their infrastructure.

```
Your Application
|
|-- JpaInteractionLoader implements InteractionLoader (port)
|-- JpaSimilarityStore   implements SimilarityStore   (port)
|-- RedisCacheAdapter    implements CachePort          (port, optional)
|
+-- RecommendationEngine (library core)
    |
    +-- InteractionMatrix    (builds the item-user matrix)
    +-- SimilarityCalculator (computes pairwise scores)
    +-- SimilarityStrategy   (algorithm: cosine, pearson, ...)
```

The domain model objects the library uses internally are plain Java records:

| Record | Description |
|---|---|
| `Interaction` | A single user-item interaction: userId, itemId, score |
| `SimilarityResult` | A precomputed similarity pair: itemId1, itemId2, score |
| `RecommendationResult` | A query result: itemId, aggregated score |

These records are the only objects that cross the boundary between the library and the caller's code.

---

## Configuration Reference

All configuration is done through `RecommendationConfig`, which is constructed via a fluent builder.

```java
RecommendationConfig config = RecommendationConfig.builder()
    .similarityThreshold(0.15)        // minimum cosine score to retain a pair
    .minCommonUsers(2)                // minimum shared users for a pair
    .topKSimilar(50)                  // max similar items stored per item
    .batchSize(1000)                  // interactions loaded per batch
    .saveBatchSize(1000)              // similarity pairs flushed per save
    .strategy(new CosineSimilarityStrategy())  // algorithm (default: cosine)
    .cachePort(new NoOpCachePort())   // caching (default: no-op)
    .build();
```

| Parameter | Default | Description |
|---|---|---|
| `similarityThreshold` | `0.15` | Pairs with a score below this value are discarded |
| `minCommonUsers` | `2` | Pairs where fewer than this many users interacted with both items are discarded |
| `topKSimilar` | `50` | Upper bound on the number of similar items stored per item |
| `batchSize` | `1000` | Number of interactions loaded per `InteractionLoader.loadBatch` call |
| `saveBatchSize` | `1000` | Number of similarity pairs flushed to the store per save call |
| `strategy` | `CosineSimilarityStrategy` | The similarity algorithm |
| `cachePort` | `NoOpCachePort` | Cache implementation; if omitted, caching is disabled |

For quick usage with all defaults:

```java
RecommendationConfig config = RecommendationConfig.defaults();
```

---

## Implementing the Ports

### InteractionLoader

This port is the data source for the engine. You implement it to bridge the library with your storage layer. The library calls `loadBatch` repeatedly, starting at `offset = 0` and incrementing by `limit` each time, until an empty list is returned.

```java
public class MyInteractionLoader implements InteractionLoader {

    private final ReviewRepository reviewRepo;
    private final OrderItemRepository orderItemRepo;
    private final WishlistRepository wishlistRepo;

    @Override
    public List<Interaction> loadBatch(int offset, int limit) {
        int page = offset / limit;
        List<Interaction> results = new ArrayList<>();

        // Reviews: rating >= 3, weight = rating score
        reviewRepo.findAll(PageRequest.of(page, limit)).forEach(r -> {
            if (r.getRating() >= 3) {
                results.add(new Interaction(
                    r.getUser().getId(),
                    r.getProduct().getId(),
                    r.getRating() * 1.0
                ));
            }
        });

        // Orders: fixed weight = 5.0
        orderItemRepo.findAll(PageRequest.of(page, limit)).forEach(item -> {
            results.add(new Interaction(
                item.getOrder().getUser().getId(),
                item.getProduct().getId(),
                5.0
            ));
        });

        // Wishlists: fixed weight = 3.0
        wishlistRepo.findAll(PageRequest.of(page, limit)).forEach(w -> {
            w.getProducts().forEach(p ->
                results.add(new Interaction(w.getUser().getId(), p.getId(), 3.0))
            );
        });

        return results;
    }
}
```

Important notes:

- If the same (userId, itemId) pair appears more than once in the results of a single batch or across multiple batches, the library merges them by taking the maximum score.
- The data source can be a relational database, NoSQL, a CSV file, or anything else.
- The engine does not assume any particular ordering of the batches.

### SimilarityStore

This port is responsible for persisting and retrieving precomputed similarity data. The library calls `deleteAll` before a recalculation, `saveAll` in batches during computation, and `findSimilar` during queries.

```java
public class JpaSimilarityStore implements SimilarityStore {

    private final ProductSimilarityRepository repo;

    @Override
    public void saveAll(List<SimilarityResult> results) {
        List<ProductSimilarity> entities = results.stream()
            .map(r -> ProductSimilarity.builder()
                .product1Id(r.itemId1())
                .product2Id(r.itemId2())
                .score(r.score())
                .build())
            .toList();
        repo.saveAll(entities);
    }

    @Override
    public List<SimilarityResult> findSimilar(int itemId, int topK) {
        return repo.findTopByItemIdOrderByScoreDesc(itemId, PageRequest.of(0, topK))
            .stream()
            .map(e -> new SimilarityResult(e.getProduct1Id(), e.getProduct2Id(), e.getScore()))
            .toList();
    }

    @Override
    public void deleteAll() {
        repo.deleteAllInBatch();
    }
}
```

The library ships a fully functional `InMemorySimilarityStore` that stores all data in a `ConcurrentHashMap`. This is suitable for tests and small-scale applications where persistence across restarts is not required.

### CachePort (optional)

By default, the library uses `NoOpCachePort`, which performs no caching. To enable caching, provide an implementation of `CachePort`.

```java
public class RedisCacheAdapter implements CachePort {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public void evictByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
```

The library evicts all cache entries for similar-item queries and user recommendation queries at the end of each `calculateAndStoreSimilarities` run.

---

## Querying the Engine

### Get similar items for an item

```java
// Returns up to 10 items most similar to item 42, ordered by score descending
List<RecommendationResult> similar = engine.getSimilarItems(42, 10);

for (RecommendationResult result : similar) {
    int candidateItemId = result.itemId();
    double score = result.score();
    // Load the full item from your repository using candidateItemId
}
```

### Get personalized recommendations for a user

You must first collect the set of item IDs the user has already interacted with. This is the caller's responsibility because the library does not have access to your data model.

```java
Set<Integer> userHistory = orderItemRepo.findProductIdsByUserId(userId);
userHistory.addAll(wishlistRepo.findProductIdsByUserId(userId));

List<RecommendationResult> recommendations = engine.getRecommendationsForUser(
    userId, userHistory, 20
);
```

If the user's history is empty, or if no similar items are found for any of their history items, the method returns an empty list. You should implement a fallback in your application, such as returning popular items.

---

## Extending with Custom Similarity Strategies

The `SimilarityStrategy` interface is a functional interface. You can implement any algorithm that operates on two sparse vectors represented as `Map<Integer, Double>`.

```java
public class PearsonSimilarityStrategy implements SimilarityStrategy {

    @Override
    public double compute(Map<Integer, Double> v1, Map<Integer, Double> v2) {
        Set<Integer> common = new HashSet<>(v1.keySet());
        common.retainAll(v2.keySet());

        if (common.isEmpty()) return 0.0;

        double n = common.size();
        double sum1 = common.stream().mapToDouble(v1::get).sum();
        double sum2 = common.stream().mapToDouble(v2::get).sum();
        double mean1 = sum1 / n;
        double mean2 = sum2 / n;

        double num = 0, den1 = 0, den2 = 0;
        for (int u : common) {
            double d1 = v1.get(u) - mean1;
            double d2 = v2.get(u) - mean2;
            num  += d1 * d2;
            den1 += d1 * d1;
            den2 += d2 * d2;
        }

        if (den1 == 0 || den2 == 0) return 0.0;
        return num / (Math.sqrt(den1) * Math.sqrt(den2));
    }
}
```

Register it in the configuration:

```java
RecommendationConfig config = RecommendationConfig.builder()
    .strategy(new PearsonSimilarityStrategy())
    .build();
```

---

## Running the Calculation

The library does not schedule the calculation for you. You are responsible for triggering `calculateAndStoreSimilarities()` at the appropriate time in your application.

**Using Spring @Scheduled:**

```java
@Scheduled(cron = "0 0 2 * * ?")  // every day at 2:00 AM
@Transactional
public void runNightlyRecalculation() {
    engine.calculateAndStoreSimilarities();
}
```

**Using a ScheduledExecutorService (plain Java):**

```java
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(
    engine::calculateAndStoreSimilarities,
    0,
    24,
    TimeUnit.HOURS
);
```

**Triggering manually (e.g., from a management endpoint):**

```java
@PostMapping("/admin/recommendations/recalculate")
public ResponseEntity<String> triggerRecalculation() {
    engine.calculateAndStoreSimilarities();
    return ResponseEntity.ok("Recalculation complete");
}
```

---

## Integration with Spring Boot

Below is a complete configuration class for integrating the library into a Spring Boot application.

```java
@Configuration
public class RecommendationConfig {

    @Bean
    public InteractionLoader interactionLoader(
            ReviewRepository reviewRepo,
            OrderItemRepository orderRepo,
            WishlistRepository wishlistRepo) {
        return new JpaInteractionLoader(reviewRepo, orderRepo, wishlistRepo);
    }

    @Bean
    public SimilarityStore similarityStore(ProductSimilarityRepository repo) {
        return new JpaSimilarityStore(repo);
    }

    @Bean
    public CachePort cachePort(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheAdapter(redisTemplate);
    }

    @Bean
    public RecommendationEngine recommendationEngine(
            InteractionLoader loader,
            SimilarityStore store,
            CachePort cache) {
        RecommendationConfig config = RecommendationConfig.builder()
            .similarityThreshold(0.15)
            .minCommonUsers(2)
            .topKSimilar(50)
            .batchSize(1000)
            .cachePort(cache)
            .build();
        return new RecommendationEngine(loader, store, config);
    }
}
```
#### Implement and overide InteractionLoader
```java
@Component
public class JpaInteractionLoader implements InteractionLoader {

    //Replace these with your actual repositories
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final WishlistRepository wishlistRepository;

    public JpaInteractionLoader(
            ReviewRepository reviewRepository,
            OrderItemRepository orderItemRepository,
            WishlistRepository wishlistRepository) {
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.wishlistRepository = wishlistRepository;
    }

    /**
     * Called repeatedly by the library with offset=0, 1000, 2000..
     * until this method returns an empty list.
     */
    @Override
    public List<Interaction> loadBatch(int offset, int limit) {
        int perSource = Math.max(1, limit / 3);
        int page = offset / limit;

        List<Interaction> result = new ArrayList<>();

        //Reviews (rating >= 3 only
        reviewRepository
            .findByRatingGreaterThanEqual(3, PageRequest.of(page, perSource))
            .forEach(review -> result.add(new Interaction(
                review.getUserId(),
                review.getProductId(),
                review.getRating()          // use actual rating as score
            )));

        // Source 2: Purchases
        orderItemRepository
            .findAll(PageRequest.of(page, perSource))
            .forEach(orderItem -> result.add(new Interaction(
                orderItem.getUserId(),
                orderItem.getProductId(),
                5.0                         // purchase = strongest signal
            )));

        //Source 3: Wishlist
        wishlistRepository
            .findAll(PageRequest.of(page, perSource))
            .forEach(wishlist -> result.add(new Interaction(
                wishlist.getUserId(),
                wishlist.getProductId(),
                3.0                         // wishlist = intent signal
            )));

        return result;
    }
}
```
#### Implement amd overide SimilarityStore
```java
@Component
public class JpaSimilarityStore implements SimilarityStore {

    private final ProductSimilarityRepository repository;

    public JpaSimilarityStore(ProductSimilarityRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves a batch of similarity pairs
     * Uses saveAll() which Spring Data JPA batches into INSERT statements
     */
    @Override
    @Transactional
    public void saveAll(List<SimilarityResult> results) {
        List<ProductSimilarity> entities = results.stream()
                .map(r -> new ProductSimilarity(r.itemId1(), r.itemId2(), r.score()))
                .toList();
        repository.saveAll(entities);
    }

    /**
     * Retrieves the top-K most similar products for a given product
     * Requires the index: idx_product1_score on (product1_id, score DESC)
     */
    @Override
    @Transactional(readOnly = true)
    public List<SimilarityResult> findSimilar(int itemId, int topK) {
        return repository
                .findSimilarProducts(itemId, PageRequest.of(0, topK))
                .stream()
                .map(ps -> new SimilarityResult(
                        ps.getProduct1Id(),
                        ps.getProduct2Id(),
                        ps.getScore()))
                .toList();
    }

    /**
     * Wipes all similarity data before a full recalculation
     */
    @Override
    @Transactional
    public void deleteAll() {
        repository.deleteAllInBatch();
    }
}
```
### If using Redis for cache - you needding implements CachePort
```java
@Component
public class RedisCacheAdapter implements CachePort {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public void evictByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
```

### Create a Scheduled job for calculation similarity

```java
@Component
public class SimilarityScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimilarityScheduler.class);

    private final RecommendationEngine engine;

    public SimilarityScheduler(RecommendationEngine engine) {
        this.engine = engine;
    }

    /**
     * Runs the full similarity recalculation pipelin
     * Default: every night at 2:00 AM
     */
    @Scheduled(cron = "${itemcf.schedule.cron:0 0 2 * * *}")
    public void recalculate() {
        log.info("Scheduled ItemCF recalculation started");
        try {
            engine.calculateAndStoreSimilarities();
            log.info("Scheduled ItemCF recalculation completed successfully");
        } catch (Exception e) {
            log.error("Scheduled ItemCF recalculation failed", e);
        }
    }
}
```

Then inject `RecommendationEngine` wherever you need it:

```java
@Service
public class ProductServiceImpl {

    private final RecommendationEngine engine;

    public List<ProductResponse> getSimilarProducts(int productId, int limit) {
        return engine.getSimilarItems(productId, limit)
            .stream()
            .map(r -> productRepository.findById(r.itemId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ProductResponse::from)
            .toList();
    }
}
```

---

## Testing

The library ships `InMemorySimilarityStore` and `NoOpCachePort` specifically to support testing without infrastructure dependencies.

```java
@Test
void engine_shouldRecommendSimilarItems() {
    List<Interaction> interactions = List.of(
        new Interaction(1, 10, 5.0),
        new Interaction(1, 20, 5.0),
        new Interaction(2, 10, 4.0),
        new Interaction(2, 20, 3.0)
    );

    InMemorySimilarityStore store = new InMemorySimilarityStore();
    RecommendationEngine engine = new RecommendationEngine(
        (offset, limit) -> offset == 0 ? interactions : List.of(),
        store,
        RecommendationConfig.defaults()
    );

    engine.calculateAndStoreSimilarities();

    List<RecommendationResult> similar = engine.getSimilarItems(10, 5);
    assertThat(similar).extracting(RecommendationResult::itemId).contains(20);
}
```

To run all library tests:

```bash
mvn test
```

Test results:

```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Project Structure

```
src/
  main/java/io/github/thacbao/itemcf/
    api/
      RecommendationEngine.java         Main public facade
    config/
      RecommendationConfig.java         Immutable config with fluent builder
    core/
      InteractionMatrix.java            Batch-loads and builds the item-user matrix
      SimilarityCalculator.java         Computes pairwise similarities
    model/
      Interaction.java                  Input record: userId, itemId, score
      SimilarityResult.java             Pre-computed pair: itemId1, itemId2, score
      RecommendationResult.java         Query result: itemId, score
    port/
      InteractionLoader.java            Port: supply interaction data
      SimilarityStore.java              Port: persist and retrieve similarity pairs
      CachePort.java                    Port: optional cache layer
      impl/
        NoOpCachePort.java              Default: no-op cache
        InMemorySimilarityStore.java    Default: in-memory store for testing

  test/java/io/github/thacbao/itemcf/
    api/
      RecommendationEngineTest.java     7 integration tests for the full pipeline
    core/
      InteractionMatrixTest.java        4 unit tests for matrix construction
    similarity/
      CosineSimilarityStrategyTest.java 9 unit tests for cosine similarity
```

---

## Roadmap

The following improvements are planned for future versions.

**Performance**

- Parallel pairwise similarity computation using `ForkJoinPool` to reduce the O(n^2) wall time on large catalogs
- Top-K optimization using a `PriorityQueue` min-heap to avoid full sorting of all candidate pairs
- Incremental update support: recalculate only items with new interactions since the last run

**Optional Modules**

- `itemcf-caffeine`: a pre-built Caffeine cache adapter as a separate optional artifact
- `itemcf-redis`: a pre-built Spring Data Redis cache adapter as a separate optional artifact

**Publishing**

- Register with Sonatype OSSRH and configure `maven-gpg-plugin` for Maven Central publishing
- Provide a GitHub Packages installation option for internal use

---

## Contributing

Contributions are welcome. To contribute:

1. Fork the repository
2. Create a feature branch
3. Implement your change with tests
4. Run `mvn clean install` to confirm all tests pass
5. Open a pull request with a clear description of the change

Please keep each pull request focused on a single concern. New similarity strategies, port implementation examples, and performance improvements are all welcome.

---

## License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
