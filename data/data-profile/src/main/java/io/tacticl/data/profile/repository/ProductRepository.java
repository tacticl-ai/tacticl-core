package io.tacticl.data.profile.repository;

import io.tacticl.data.profile.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** User-registered products ({@code products}). */
public interface ProductRepository extends MongoRepository<Product, String> {

    /** A user's active products, newest first. */
    List<Product> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(String userId);

    /** A single product scoped to its owning user (ownership check). */
    Optional<Product> findByIdAndUserId(String id, String userId);
}
