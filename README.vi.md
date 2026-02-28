# ItemCF — Thư viện Item-based Collaborative Filtering cho JAV

Thư viện Java core triển khai dựa trên Machine Learning Item-based Collaborative Filtering (ItemCF), tính độ tương đồng giữa các sản phẩm. Thư viện có thể dùng ở bất kỳ ứng dụng nào có JVM mà k cần Spring, JPA....
---

## Phụ lục

- [Tổng quản](#tong-quan)
- [Hoạt động](#cach-hoat-dong)
- [Requirements](#yeu-cau-he-thong)
- [Setup](#cai-dat)
- [Quickstart](#bat-dau-nhanh)
- [architecture](#kien-truc)
- [Cấu hình](#tham-chieu-cau-hinh)
- [Implements](#trien-khai-cac-port)
  - [InteractionLoader](#interactionloader)
  - [SimilarityStore](#similaritystore)
  - [CachePort (tuy chon)](#cacheport-tuy-chon)
- [Truy vấn](#truy-van-tu-may-tinh-khuyen-nghi)
- [Mở rộng](#mo-rong-voi-chien-luoc-tuong-dong-tuy-chinh)
- [Cron job](#chay-qua-trinh-tinh-toan)
- [Tích hợp vào Spring Boot](#tich-hop-voi-spring-boot)
- [TEst](#kiem-thu)
- [Project Structure](#cau-truc-du-an)
- [Phát triển](#lo-trinh-phat-trien)
- [Đóng góp](#dong-gop)
- [Giấp phép](#giay-phep)

---

## Tổng quan

Item-based Collaborative Filtering là kỹ thuật hệ khuyến nghị tìm kiếm các sản phẩm tương tự với những sản phẩm mà người dùng đã tương tác, sau đó đề xuất chúng trong hệ thống gợi ý sản phẩm. Thuật toán hoạt động bằng cách phân tích các ma trận tương tác của người dùng trên toàn bộ danh mục sản phẩm: những sản phẩm nào thường được tương tác bởi cùng một nhóm người dùng sẽ được coi là tương tự nhau.

Đặc điểm chính của thư viện:

- Không phụ thuộc runtime vào Spring Framework, JPA, Hibernate hay Redis.

- Chỉ có một phụ thuộc compile-time: SLF4J API để ghi log.

- Tuân theo Hexagonal Architecture: toàn bộ truy cập dữ liệu được ủy quyền cho các adapter do người dùng cung cấp.

- Thuật toán tương đồng có thể hoán đổi thông qua giao diện chiến lược.

- Kèm theo các triển khai in-memory của tất cả các port để kiểm thử và phát triển.
- Tương thích đầy đủ với Java 17 trở lên.

---

## Hoạt động

Quá trình chạy trên 2 giai đoạn

**Offline (calculateAndStoreSimilarities)**

Phase này được thiết kế để chạy cron job (ví dụ: hàng đêm). Nó thực hiện các bước sau:

1. Tải toàn bộ các tương tác người dùng - sản phẩm từ nguồn dữ liệu theo lô bởi qua InteractionLoader

2. Xây dựng ma trận phân thừa sản phẩm - người dùng, trong đó mỗi sản phẩm được biểu diễn là một vector điểm người dùng

3. Tính cosine similarity cho tất cả các cặp sản phẩm duy nhất

4. Loại bỏ các cặp có điểm dưới ngưỡng hoặc có quá ít người dùng chung

5. Lưu trữ các cặp dữ liệu sang store thông qua SimilarityStore

6. Xóa bỏ mọi kết quả đã cache

**Online (query)**

Hệ thống hỗ trợ 2 loại truy vấn:

- `getSimilarItems(itemId, limit)` — Tìm những sản phẩm giống với sản phẩm A nhất
- `getRecommendationsForUser(userId, userHistory, limit)` — tổng hợp tất cả sản phẩm người dùng đã tương tác và trả về sản phẩm gợi ý cho người dùng

---

## Requirements

- Java 17 trở lên
- Maven 3.6 trở lên

---

## Setup


```bash
mvn clean install
```

Import dependency

```xml
<dependency>
    <groupId>io.github.thacbao</groupId>
    <artifactId>itemcf</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

---

## Quickstart

Ví dụ dưới sử dụng `InMemorySimilarityStore` tích hợp sẵn để minh họa cho code Java đơn giản

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
        // 1. Cung cap cac tuong tac (thuong lay tu co so du lieu)
        List<Interaction> interactions = List.of(
            new Interaction(1, 101, 5.0),  // nguoi dung 1 mua san pham 101
            new Interaction(1, 102, 4.0),  // nguoi dung 1 mua san pham 102
            new Interaction(2, 101, 5.0),  // nguoi dung 2 mua san pham 101
            new Interaction(2, 103, 3.0),  // nguoi dung 2 mua san pham 103
            new Interaction(3, 102, 4.0),  // nguoi dung 3 mua san pham 102
            new Interaction(3, 103, 4.0)   // nguoi dung 3 mua san pham 103
        );

        // 2. Cau hinh bo may
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

        // 3. Chay qua trinh tinh toan ngoai tuyen
        engine.calculateAndStoreSimilarities();

        // 4. Truy van san pham tuong tu voi san pham 101
        List<RecommendationResult> similar = engine.getSimilarItems(101, 5);
        similar.forEach(r -> System.out.printf("San pham %d, diem=%.4f%n", r.itemId(), r.score()));

        // 5. Lay khuyen nghi ca nhan cho nguoi dung da tuong tac voi san pham 101
        List<RecommendationResult> forUser = engine.getRecommendationsForUser(
            4, Set.of(101), 10
        );
        forUser.forEach(r ->
            System.out.printf("San pham khuyen nghi %d, diem=%.4f%n", r.itemId(), r.score())
        );
    }
}
```

---

## architecture

Thư viện tuân theo **Hexagonal Architecture** (Ports and Adapters). Logic thuật toán cốt lõi không biết gì về cách lưu trữ hay truy xuất dữ liệu. Thay vào đó, thư viện định nghĩa một tập hợp các giao diện (port) mà người gọi phải triển khai để kết nối thư viện với cơ sở hạ tầng

```
your-app
|
|-- JpaInteractionLoader  trien khai InteractionLoader (port)
|-- JpaSimilarityStore    trien khai SimilarityStore   (port)
|-- RedisCacheAdapter     trien khai CachePort         (port, tuy chon)
|
+-- RecommendationEngine (loi cua thu vien)
    |
    +-- InteractionMatrix    (xay dung ma tran san pham - nguoi dung)
    +-- SimilarityCalculator (tinh diem tung cap)
    +-- SimilarityStrategy   (thuat toan: cosine, pearson, ...)
```

Các model record trong dự án

| Record | Mô tả |
|---|---|
| `Interaction` | Ma trận tương tác nguời dùng - sản phẩm - score |
| `SimilarityResult` | Kết quả tương đồng của 1 cặp sp1 - sp2 - score |
| `RecommendationResult` | Kết quả truy vấn tương đồng cho sp1: sp2 - score |

---

## Cấu hình

Toàn bộ được cấu hình qua `RecommendationConfig`, xây dựng bằng builder pattern

```java
RecommendationConfig config = RecommendationConfig.builder()
    .similarityThreshold(0.15)        // diem cosine toi thieu de giu lai mot cap
    .minCommonUsers(2)                // so nguoi dung chung toi thieu cho mot cap
    .topKSimilar(50)                  // so san pham tuong tu toi da luu moi san pham
    .batchSize(1000)                  // so tuong tac tai moi lo
    .saveBatchSize(1000)              // so cap tuong dong luu moi lan ghi
    .strategy(new CosineSimilarityStrategy())  // thuat toan (mac dinh: cosine)
    .cachePort(new NoOpCachePort())   // bo nho dem (mac dinh: khong lam gi)
    .build();
```

| Tham số | Mặc đinh | Mô tả |
|---|---|---|
| `similarityThreshold` | `0.15` | các cặp có giá trị sim bé hơn sẽ bị loại |
| `minCommonUsers` | `2` | Các cặp sp có số lượng user cùng đánh giá cả 2 sp ít hơn 2 sẽ bị loại |
| `topKSimilar` | `50` | Số sản phẩm tương tự giới hạn được chọn |
| `batchSize` | `1000` | Số tương tác của user với item mỗi lần gọi `InteractionLoader.loadBatch` |
| `saveBatchSize` | `1000` | Số cặp tương đồng được ghi vào store mỗi lần |
| `strategy` | `CosineSimilarityStrategy` | Thuật toán tính độ tương đồng |
| `cachePort` | `NoOpCachePort` | Triển khai cache hoặc bỏ qua |

Để sử dụng nhanh với các giá trị mặc định:

```java
RecommendationConfig config = RecommendationConfig.defaults();
```

---

## IMplement

### InteractionLoader

Port này là nguồn dữ liệu ban đầu để tính cosine similarity. Kết nối với nơi lưu trữ dữ liệu, truy vấn cho đến khi hết dữ liệu.

```java
public class MyInteractionLoader implements InteractionLoader {

    private final ReviewRepository reviewRepo;
    private final OrderItemRepository orderItemRepo;
    private final WishlistRepository wishlistRepo;

    @Override
    public List<Interaction> loadBatch(int offset, int limit) {
        int page = offset / limit;
        List<Interaction> results = new ArrayList<>();

        // Danh gia: rating >= 3, trong so = diem danh gia
        reviewRepo.findAll(PageRequest.of(page, limit)).forEach(r -> {
            if (r.getRating() >= 3) {
                results.add(new Interaction(
                    r.getUser().getId(),
                    r.getProduct().getId(),
                    r.getRating() * 1.0
                ));
            }
        });

        // Don hang: trong so co dinh = 5.0
        orderItemRepo.findAll(PageRequest.of(page, limit)).forEach(item -> {
            results.add(new Interaction(
                item.getOrder().getUser().getId(),
                item.getProduct().getId(),
                5.0
            ));
        });

        // Wishlist: trong so co dinh = 3.0
        wishlistRepo.findAll(PageRequest.of(page, limit)).forEach(w -> {
            w.getProducts().forEach(p ->
                results.add(new Interaction(w.getUser().getId(), p.getId(), 3.0))
            );
        });

        return results;
    }
}
```

Lưu ý:

- Nếu cùng một cặp (userId, itemId) xuất hiện nhiều lần từ các nguồn khác nhau, thư viện hợp nhất chung bằng cách lấy điểm tối đa.

- Nguồn dữ liệu có thể là cơ sở dữ liệu quan hệ, NoSQL, file CSV, hoặc bất kỳ gì khác.

- Không giả định bất kỳ thứ tự nào của các lô dữ liệu.

### SimilarityStore

Port này chịu trách nhệm lưu trữ và truy xuất dữ liệu tương đồng đã có từ trước. Thư viện gọi `deleteAll` trước khi tính mới, `saveAll` theo từng lô, và `findSimilar` trong quá trình truy vấn

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


### CachePort (optional)

Mặc định thư viện sử dụng `NoOpCachePort`, không thực hiện cache. để bắt cache, bạn có thể implements `CachePort`.

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

Thư viện xóa toàn bộ entry cache cho các truy vấn sản phẩm tương ở cuối mỗi lần chạy `calculateAndStoreSimilarities`

---

## Truy vấn

### Lấy sản phẩm tương tự với 1 sp

```java
// Tra ve toi da 10 san pham tuong tu nhat voi san pham 42, sap xep theo diem giam dan
List<RecommendationResult> similar = engine.getSimilarItems(42, 10);

for (RecommendationResult result : similar) {
    int candidateItemId = result.itemId();
    double score = result.score();
    // Tai san pham day du tu repository cua ban bang candidateItemId
}
```

### Lấy sản phẩm gợi ý cho người dùng

Đầu tiên cần tìm tập sản phẩm user đã tương tác ( review, purchases, like...)

```java
Set<Integer> userHistory = orderItemRepo.findProductIdsByUserId(userId);
userHistory.addAll(wishlistRepo.findProductIdsByUserId(userId));

List<RecommendationResult> recommendations = engine.getRecommendationsForUser(
    userId, userHistory, 20
);
```

Nếu lịch sử của người dùng trống, phương thức trả về danh sách rỗng

---

## Mở rộng

`SimilarityStrategy` là 1 functional interface, bạn có thể override bất kỳ thuật toán nào hoạt động trên 2 vector cho trước `Map<Integer, Double>`

```java
public class PearsonSimilarityStrategy implements SimilarityStrategy {

    @Override
    public double compute(Map<Integer, Double> v1, Map<Integer, Double> v2) {
        Set<Integer> common = new HashSet<>(v1.keySet());
        common.retainAll(v2.keySet());

        if (common.isEmpty()) return 0.0;

        double n = common.size();
        double mean1 = common.stream().mapToDouble(v1::get).sum() / n;
        double mean2 = common.stream().mapToDouble(v2::get).sum() / n;

        double tu = 0, mau1 = 0, mau2 = 0;
        for (int u : common) {
            double d1 = v1.get(u) - mean1;
            double d2 = v2.get(u) - mean2;
            tu   += d1 * d2;
            mau1 += d1 * d1;
            mau2 += d2 * d2;
        }

        if (mau1 == 0 || mau2 == 0) return 0.0;
        return tu / (Math.sqrt(mau1) * Math.sqrt(mau2));
    }
}
```

Cấu hình:

```java
RecommendationConfig config = RecommendationConfig.builder()
    .strategy(new PearsonSimilarityStrategy())
    .build();
```

---

## Cron job

Thư viện không tự động đặt lịch để chạy tính toán. Bạn sẽ tự tạo cron job cho `calculateAndStoreSimilarities` vào thời điểm bất kỳ theo mỗi chu kỳ.

**Sử dụng @Scheduled:**

```java
@Scheduled(cron = "0 0 2 * * ?")  // moi ngay luc 2:00 SA
@Transactional
public void chayTinhToanHangDem() {
    engine.calculateAndStoreSimilarities();
}
```

**Sử dụng ScheduledExecutorService (Java thuan):**

```java
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(
    engine::calculateAndStoreSimilarities,
    0,
    24,
    TimeUnit.HOURS
);
```

**Thủ công (ví dụ management endpoint):**

```java
@PostMapping("/admin/recommendations/recalculate")
public ResponseEntity<String> kichHoatTaiTinh() {
    engine.calculateAndStoreSimilarities();
    return ResponseEntity.ok("Tinh toan hoan tat");
}
```

---

## Tích hợp vào Spring Boot

Dưới đây là class cấu hình:

```java
@Configuration
public class RecommendationConfiguration {

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
Sau đó inject `RecommendationEngine` bất cứ khi nào cần:

```java
@Service
public class ProductServiceImpl {

    private final RecommendationEngine engine;
    private final ProductRepository productRepository;

    public List<ProductResponse> laySanPhamTuongTu(int productId, int limit) {
        return engine.getSimilarItems(productId, limit)
            .stream()
            .map(r -> productRepository.findById(r.itemId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ProductResponse::from)
            .toList();
    }

    public List<ProductResponse> layKhuyenNghiChoNguoiDung(int userId, int limit) {
        Set<Integer> lichSu = layLichSuTuongTac(userId);

        if (lichSu.isEmpty()) {
            return laySanPhamPhoBien(limit);
        }

        List<RecommendationResult> khuyen = engine.getRecommendationsForUser(userId, lichSu, limit);

        if (khuyen.isEmpty()) {
            return laySanPhamPhoBien(limit);
        }

        return khuyen.stream()
            .map(r -> productRepository.findById(r.itemId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ProductResponse::from)
            .toList();
    }
}
```

---

## Test


```java
@Test
void engine_nenKhuyenNghiSanPhamTuongTu() {
    List<Interaction> tuongTac = List.of(
        new Interaction(1, 10, 5.0),
        new Interaction(1, 20, 5.0),
        new Interaction(2, 10, 4.0),
        new Interaction(2, 20, 3.0)
    );

    InMemorySimilarityStore store = new InMemorySimilarityStore();
    RecommendationEngine engine = new RecommendationEngine(
        (offset, limit) -> offset == 0 ? tuongTac : List.of(),
        store,
        RecommendationConfig.defaults()
    );

    engine.calculateAndStoreSimilarities();

    List<RecommendationResult> tuongTu = engine.getSimilarItems(10, 5);
    assertThat(tuongTu).extracting(RecommendationResult::itemId).contains(20);
}
```

Để chạy tất cả test

```bash
mvn test
```

Kết quả test:

```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Lop kiem thu | So bai | Noi dung |
|---|---|---|
| `CosineSimilarityStrategyTest` | 9 | Vector dong nhat, khong giao nhau, doi xung, giao mot phan, pham vi ket qua |
| `InteractionMatrixTest` | 4 | Xay dung ma tran dung, hop nhat trung lap bang max, du lieu trong, nhieu lo |
| `RecommendationEngineTest` | 7 | Toan bo pipeline, loc san pham da xem, lich su trong, tai tinh idempotent |

---

## Cấu trúc dự án

```
src/
  main/java/io/github/thacbao/itemcf/
    api/
      RecommendationEngine.java         Mat tien cong khai chinh
    config/
      RecommendationConfig.java         Cau hinh bat bien voi fluent builder
    core/
      InteractionMatrix.java            Tai va xay dung ma tran san pham-nguoi dung
      SimilarityCalculator.java         Tinh tuong dong tung cap
    model/
      Interaction.java                  Record dau vao: userId, itemId, score
      SimilarityResult.java             Cap da tinh: itemId1, itemId2, score
      RecommendationResult.java         Ket qua truy van: itemId, score
    port/
      InteractionLoader.java            Port: cung cap du lieu tuong tac
      SimilarityStore.java              Port: luu va truy xuat cap tuong dong
      CachePort.java                    Port: tang cache tuy chon
      impl/
        NoOpCachePort.java              Mac dinh: cache khong lam gi
        InMemorySimilarityStore.java    Mac dinh: in-memory de kiem thu
    similarity/
      SimilarityStrategy.java           Giao dien chien luoc thuat toan
      CosineSimilarityStrategy.java     Trien khai mac dinh: cosine

  test/java/io/github/thacbao/itemcf/
    api/
      RecommendationEngineTest.java     7 kiem thu tich hop toan bo pipeline
    core/
      InteractionMatrixTest.java        4 kiem thu don vi xay dung ma tran
    similarity/
      CosineSimilarityStrategyTest.java 9 kiem thu don vi cosine similarity
```

---

## Phát triển

Các cải tiển tương lai

**Hiệu năng**
Tính tương đồng song song sử dụng `ForkJoinPool` để giảm thời gian O(n^2) trên danh mục lớn.

Tối ưu hóa Top-K sử dụng `PriorityQueue` min-heap để tránh sắp xếp toàn bộ các ứng viên.

Hỗ trợ cập nhật gia tăng: chỉ tính lại những sản phẩm có tương tác mới kể từ lần chạy cuối.

**Module tùy chọn**

- `itemcf-caffeine`:  một adapter cache Caffeine được xây dựng sẵn dưới dạng artifact tùy chọn riêng biệt.

- `itemcf-redis`: một adapter Spring Data Redis được xây dựng sẵn dưới dạng artifact tùy chọn riêng biệt.

**Xuất bản**

Đăng ký với Sonatype OSSRH và cấu hình `maven-gpg-plugin` để xuất bản lên Maven Central.

Cung cấp tùy chọn cài đặt qua GitHub Packages cho sử dụng nội bộ trong tổ chức.

---

## Contribute

Để contribute

1. Fork repository
2. Tạo nhánh mới
3. Triển khai thay đổi của bạn
4. Run và test
5. Tạo Pull request

---

## Giấy phép

Dự án cấp phép theo Apache License, Phien ban 2.0. Xem file LICENSE để biết chi tiết.
