package com.example.reactor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

record Product(String sku, String name, BigDecimal price) { }

record Stock(String sku, int available, boolean known) {
    static Stock unknown(String sku) {
        return new Stock(sku, 0, false);
    }
}

record Review(String author, int rating, String text) { }

record ProductPage(Product product, Stock stock, List<Review> reviews) { }

interface CatalogClient {
    Mono<Product> getProduct(String sku);
}

interface InventoryClient {
    Mono<Stock> getStock(String sku);
}

interface ReviewClient {
    Flux<Review> getReviews(String sku);
}

public class ProductPageService {
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(2);

    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final ReviewClient reviews;

    public ProductPageService(CatalogClient catalog, InventoryClient inventory, ReviewClient reviews) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.reviews = reviews;
    }

    public Mono<ProductPage> productPage(String sku) {
        return Mono.defer(() -> {
            Mono<Product> productMono = catalog.getProduct(sku)
                    .timeout(CLIENT_TIMEOUT); // Catalog errors propagate.

            Mono<Stock> stockMono = inventory.getStock(sku)
                    .timeout(CLIENT_TIMEOUT)
                    .onErrorReturn(Stock.unknown(sku));

            Mono<List<Review>> reviewsMono = reviews.getReviews(sku)
                    .collectList()
                    .timeout(CLIENT_TIMEOUT)
                    .onErrorReturn(List.of());

            // zip subscribes to all three sources without blocking a thread.
            return Mono.zip(productMono, stockMono, reviewsMono)
                    .map(tuple -> new ProductPage(tuple.getT1(), tuple.getT2(), tuple.getT3()));
        });
    }
}
