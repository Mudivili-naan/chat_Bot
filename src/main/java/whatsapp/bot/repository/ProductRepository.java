package whatsapp.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import whatsapp.bot.entity.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryAndIsActiveTrue(String category);

    List<Product> findByIsActiveTrue();

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.isActive = true ORDER BY p.category ASC")
    List<String> findDistinctCategories();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.category = :category AND p.isActive = true")
    List<Product> findByCategoryWithVariants(@Param("category") String category);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findByIdWithVariants(@Param("id") Long id);
}
