package whatsapp.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import whatsapp.bot.entity.ProductVariant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
	
	@Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product WHERE pv.id = :id")
    Optional<ProductVariant> findByIdWithProduct(@Param("id") Long id);
    
	List<ProductVariant> findByProductIdAndIsActiveTrue(Long productId);
}
