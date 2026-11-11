package whatsapp.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import whatsapp.bot.entity.Order;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.variant v LEFT JOIN FETCH v.product WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.variant v LEFT JOIN FETCH v.product WHERE o.customer.phoneNumber = :phone ORDER BY o.createdAt DESC")
    List<Order> findAllByCustomerPhoneWithItems(@Param("phone") String phone);

    Optional<Order> findTopByCustomerPhoneNumberOrderByCreatedAtDesc(String customerPhone);

    Optional<Order> findByPaymentLinkId(String paymentLinkId);
}
