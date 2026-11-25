package com.sardor.ecommerce.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.sardor.ecommerce.config.RateLimitCounter;
import com.sardor.ecommerce.config.RedisRateLimitCounter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostgresApiIntegrationTest {
    private static final String ADMIN_EMAIL = "integration-admin@example.com";
    private static final String ADMIN_PASSWORD = "integration-admin-password-2026";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ecommerce_it")
            .withUsername("integration")
            .withPassword("integration-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.rate-limit.backend", () -> "redis");
        registry.add("app.observability.prometheus-token", () -> "integration-metrics-secret");
        registry.add("app.security.jwt.secret-base64", () ->
                Base64.getEncoder().encodeToString(
                        "integration-test-secret-which-is-exactly-32-bytes".substring(0, 32)
                                .getBytes(StandardCharsets.UTF_8)));
        registry.add("app.bootstrap-admin.enabled", () -> true);
        registry.add("app.bootstrap-admin.email", () -> ADMIN_EMAIL);
        registry.add("app.bootstrap-admin.password", () -> ADMIN_PASSWORD);
        registry.add("app.rate-limit.auth-requests", () -> 100);
        registry.add("app.rate-limit.checkout-requests", () -> 100);
        registry.add("app.rate-limit.payment-requests", () -> 100);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired RateLimitCounter rateLimitCounter;

    @Test
    void redisRateLimitCounterSharesAtomicWindowAcrossIndependentInstances() {
        String key = "integration:" + UUID.randomUUID();
        Duration window = Duration.ofSeconds(30);

        RateLimitCounter.Decision first = rateLimitCounter.increment(key, window);
        RateLimitCounter.Decision second = new RedisRateLimitCounter(redisTemplate).increment(key, window);

        assertEquals(1L, first.requestCount());
        assertEquals(2L, second.requestCount());
        assertTrue(second.remainingWindowMillis() > 0);
    }

    @Test
    void prometheusEndpointRequiresDedicatedScraperCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());

        String credentials = Base64.getEncoder().encodeToString(
                "prometheus:integration-metrics-secret".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Basic " + credentials))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("# HELP")));
    }

    @Test
    void flywayAppliesAllVersionedMigrationsOnRealPostgres() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        Integer paymentTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'payment_attempts'",
                Integer.class);
        Integer inventoryTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'inventory_movements'",
                Integer.class);

        assertEquals(5, successfulMigrations);
        assertEquals(1, paymentTableCount);
        assertEquals(1, inventoryTableCount);
    }

    @Test
    void customerCanRegisterCheckoutAndCompleteSimulatedPayment() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String customerToken = registerCustomer(uniqueEmail("buyer"));
        String productId = createProduct(adminToken, uniqueSku("KB"), 5);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new AddItemBody(productId, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));

        MvcResult checkout = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", bearer(customerToken))
                        .header("Idempotency-Key", "checkout-" + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(159.8))
                .andReturn();

        String orderId = objectMapper.readTree(checkout.getResponse().getContentAsByteArray())
                .path("id").asString();

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .header("Authorization", bearer(customerToken))
                        .header("Idempotency-Key", "payment-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"APPROVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        Integer remainingStock = jdbcTemplate.queryForObject(
                "select stock from products where id = ?", Integer.class, UUID.fromString(productId));
        Integer reservationRows = jdbcTemplate.queryForObject(
                "select count(*) from inventory_movements where type = 'RESERVED'", Integer.class);
        Integer paymentRows = jdbcTemplate.queryForObject(
                "select count(*) from payment_attempts where status = 'SUCCEEDED'", Integer.class);
        assertEquals(3, remainingStock);
        assertTrue(reservationRows >= 1);
        assertTrue(paymentRows >= 1);
    }

    @Test
    void twoCustomersCannotOversellTheLastUnitDuringConcurrentCheckout() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String productId = createProduct(adminToken, uniqueSku("LAST"), 1);
        String customerOne = registerCustomer(uniqueEmail("one"));
        String customerTwo = registerCustomer(uniqueEmail("two"));
        addOneItem(customerOne, productId);
        addOneItem(customerTwo, productId);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> checkoutStatus(start, customerOne));
            Future<Integer> second = executor.submit(() -> checkoutStatus(start, customerTwo));
            start.countDown();

            int firstStatus = first.get(30, TimeUnit.SECONDS);
            int secondStatus = second.get(30, TimeUnit.SECONDS);
            assertTrue((firstStatus == 201 && secondStatus == 409)
                    || (firstStatus == 409 && secondStatus == 201));
        }

        Integer remainingStock = jdbcTemplate.queryForObject(
                "select stock from products where id = ?", Integer.class, UUID.fromString(productId));
        assertEquals(0, remainingStock);
    }

    @Test
    void checkoutFailureRollsBackOrderInventoryAndCartChanges() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String customerToken = registerCustomer(uniqueEmail("rollback"));
        String productId = createProduct(adminToken, uniqueSku("ROLLBACK"), 5);
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new AddItemBody(productId, 2))))
                .andExpect(status().isOk());

        String idempotencyKey = "rollback-" + UUID.randomUUID();
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_reserved_inventory_for_test ON inventory_movements");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_reserved_inventory_for_test()");
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_reserved_inventory_for_test() RETURNS trigger
                LANGUAGE plpgsql AS '
                BEGIN
                    IF NEW.type = ''RESERVED'' THEN
                        RAISE EXCEPTION ''injected inventory ledger failure'';
                    END IF;
                    RETURN NEW;
                END;
                ';
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_reserved_inventory_for_test
                BEFORE INSERT ON inventory_movements
                FOR EACH ROW EXECUTE FUNCTION fail_reserved_inventory_for_test()
                """);

        try {
            int status = mockMvc.perform(post("/api/v1/checkout")
                            .header("Authorization", bearer(customerToken))
                            .header("Idempotency-Key", idempotencyKey))
                    .andReturn().getResponse().getStatus();
            assertTrue(status >= 500, "Injected persistence failure should fail the checkout request");

            Integer remainingStock = jdbcTemplate.queryForObject(
                    "select stock from products where id = ?", Integer.class, UUID.fromString(productId));
            Integer ordersForKey = jdbcTemplate.queryForObject(
                    "select count(*) from orders where idempotency_key = ?", Integer.class, idempotencyKey);
            Integer reservationsForProduct = jdbcTemplate.queryForObject(
                    "select count(*) from inventory_movements where product_id = ? and type = 'RESERVED'",
                    Integer.class, UUID.fromString(productId));

            assertEquals(5, remainingStock);
            assertEquals(0, ordersForKey);
            assertEquals(0, reservationsForProduct);
            mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(customerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(2));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_reserved_inventory_for_test ON inventory_movements");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_reserved_inventory_for_test()");
        }
    }

    @Test
    void securityScopesCustomerRoutesAndActuatorHealthIsPublic() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String customerToken = registerCustomer(uniqueEmail("security"));

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String registerCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new RegisterBody(email, "customer-password-2026", "Integration Buyer"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("accessToken").asString();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginBody(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("accessToken").asString();
    }

    private String createProduct(String adminToken, String sku, int stock) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new CreateProductBody(sku, "Test Mechanical Keyboard", "Test item",
                                        "Test", 79.90, stock))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("id").asString();
    }

    private void addOneItem(String customerToken, String productId) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new AddItemBody(productId, 1))))
                .andExpect(status().isOk());
    }

    private int checkoutStatus(CountDownLatch start, String customerToken) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", bearer(customerToken))
                        .header("Idempotency-Key", "checkout-" + UUID.randomUUID()))
                .andReturn().getResponse().getStatus();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueSku(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegisterBody(String email, String password, String fullName) {}
    private record LoginBody(String email, String password) {}
    private record AddItemBody(String productId, int quantity) {}
    private record CreateProductBody(String sku, String name, String description, String category,
                                     double price, int stock) {}
}
