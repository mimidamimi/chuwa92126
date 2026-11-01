package com.example.reactor;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProductPageServiceTest {
    private final Product product = new Product("sku-1", "Keyboard", new BigDecimal("49.99"));

    @Test
    void combinesSuccessfulClients() {
        ProductPageService service = new ProductPageService(
                sku -> Mono.just(product),
                sku -> Mono.just(new Stock(sku, 12, true)),
                sku -> Flux.just(new Review("Alex", 5, "Great")));
        StepVerifier.create(service.productPage("sku-1"))
                .assertNext(page -> {
                    assertEquals(product, page.product());
                    assertEquals(12, page.stock().available());
                    assertEquals(1, page.reviews().size());
                }).verifyComplete();
    }

    @Test
    void substitutesFallbacksForStockAndReviewsErrors() {
        ProductPageService service = new ProductPageService(
                sku -> Mono.just(product),
                sku -> Mono.error(new IllegalStateException("inventory unavailable")),
                sku -> Flux.error(new IllegalStateException("reviews unavailable")));
        StepVerifier.create(service.productPage("sku-1"))
                .assertNext(page -> {
                    assertEquals(Stock.unknown("sku-1"), page.stock());
                    assertTrue(page.reviews().isEmpty());
                }).verifyComplete();
    }

    @Test
    void propagatesCatalogErrors() {
        RuntimeException failure = new RuntimeException("catalog unavailable");
        ProductPageService service = new ProductPageService(
                sku -> Mono.error(failure),
                sku -> Mono.just(Stock.unknown(sku)),
                sku -> Flux.empty());
        StepVerifier.create(service.productPage("sku-1"))
                .expectErrorMatches(error -> error == failure).verify();
    }

    @Test
    void substitutesFallbacksWhenStockAndReviewsTimeOut() {
        ProductPageService service = new ProductPageService(
                sku -> Mono.just(product),
                sku -> Mono.never(),
                sku -> Flux.never());
        StepVerifier.withVirtualTime(() -> service.productPage("sku-1"))
                .thenAwait(Duration.ofSeconds(2))
                .assertNext(page -> {
                    assertEquals(Stock.unknown("sku-1"), page.stock());
                    assertTrue(page.reviews().isEmpty());
                }).verifyComplete();
    }
}
