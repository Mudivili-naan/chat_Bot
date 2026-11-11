package whatsapp.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import whatsapp.bot.entity.Customer;
import whatsapp.bot.entity.Order;
import whatsapp.bot.entity.OrderItem;
import whatsapp.bot.entity.ProductVariant;
import whatsapp.bot.model.UserSession;
import whatsapp.bot.repository.CustomerRepository;
import whatsapp.bot.repository.OrderRepository;
import whatsapp.bot.repository.ProductVariantRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final CustomerRepository customerRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Transactional
    public Order placeOrder(UserSession session) {
        String phone = session.getPhoneNumber();
        String address = session.getDeliveryAddress();
        Long variantId = session.getSelectedVariantId();
        int qty = session.getQuantity() != null && session.getQuantity() > 0 ? session.getQuantity() : 1;
        String notes = session.getCustomNotes();
        String paymentMethod = session.getPaymentMethod() != null ? session.getPaymentMethod() : "PENDING";

        // 1. Find or create Customer
        Customer customer = customerRepository.findById(phone)
                .orElseGet(() -> {
                    Customer newCustomer = new Customer();
                    newCustomer.setPhoneNumber(phone);
                    newCustomer.setName(session.getCustomerName() != null ? session.getCustomerName() : "Customer");
                    newCustomer.setDeliveryAddress(address);
                    return customerRepository.save(newCustomer);
                });

        // Update address if changed
        if (address != null && !address.isBlank()) {
            customer.setDeliveryAddress(address);
            customerRepository.save(customer);
        }

        // 2. Fetch product variant from DB
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));

        // Stock deduction
        if (variant.getStockQuantity() != null && variant.getStockQuantity() >= qty) {
            variant.setStockQuantity(variant.getStockQuantity() - qty);
            variantRepository.save(variant);
        }

        // 3. Create unique tracking order number
        String orderNumber = "ORD-" + (100000 + RANDOM.nextInt(900000));

        BigDecimal total = variant.getPrice().multiply(BigDecimal.valueOf(qty));

        Order order = Order.builder()
                .customer(customer)
                .orderNumber(orderNumber)
                .deliveryAddress(address != null ? address : "Bakery Pickup")
                .customNotes(notes)
                .totalAmount(total)
                .orderStatus("PLACED")
                .paymentStatus("COD".equalsIgnoreCase(paymentMethod) ? "COD" : "PENDING")
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .variant(variant)
                .quantity(qty)
                .priceAtPurchase(variant.getPrice())
                .build();

        order.setItems(List.of(item));

        Order saved = orderRepository.save(order);
        session.setActiveOrderNumber(orderNumber);
        log.info("Order successfully created: {} for phone: {}", orderNumber, phone);
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrderByCode(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber.trim().toUpperCase())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Order getLatestOrderByPhone(String phone) {
        return orderRepository.findTopByCustomerPhoneNumberOrderByCreatedAtDesc(phone).orElse(null);
    }

    @Transactional
    public boolean updatePaymentStatus(String orderNumber, String paymentStatus, String paymentLinkId) {
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(orderNumber);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setPaymentStatus(paymentStatus);
            if (paymentLinkId != null) {
                order.setPaymentLinkId(paymentLinkId);
            }
            if ("PAID".equalsIgnoreCase(paymentStatus) && "PLACED".equalsIgnoreCase(order.getOrderStatus())) {
                order.setOrderStatus("CONFIRMED");
            }
            orderRepository.save(order);
            log.info("Payment updated for order {}: status={}", orderNumber, paymentStatus);
            return true;
        }
        return false;
    }

    public String buildTrackingCard(Order order) {
        if (order == null) {
            return "❌ No order found with this tracking ID. Please verify and try again.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📦 *ORDER STATUS: ").append(order.getOrderNumber()).append("*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n");

        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (OrderItem item : order.getItems()) {
                String pName = item.getVariant() != null && item.getVariant().getProduct() != null
                        ? item.getVariant().getProduct().getName() : "Item";
                String vName = item.getVariant() != null ? item.getVariant().getVariantName() : "";
                sb.append("• ").append(pName).append(" (").append(vName).append(")")
                        .append(" x ").append(item.getQuantity())
                        .append(" - ₹").append(item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .append("\n");
            }
        }

        sb.append("💰 *Total Amount:* ₹").append(order.getTotalAmount()).append("\n");
        sb.append("📍 *Delivery To:* ").append(order.getDeliveryAddress()).append("\n");
        if (order.getCreatedAt() != null) {
            sb.append("🕒 *Placed At:* ").append(order.getCreatedAt().format(TIME_FORMATTER)).append("\n");
        }
        sb.append("💳 *Payment:* ").append(formatPaymentStatus(order.getPaymentStatus())).append("\n\n");

        sb.append("📍 *Live Progress:*\n");
        sb.append(buildStatusBar(order.getOrderStatus())).append("\n");

        return sb.toString();
    }

    private String formatPaymentStatus(String status) {
        if (status == null) return "Pending";
        return switch (status.toUpperCase()) {
            case "PAID" -> "✅ Paid Online";
            case "COD" -> "💵 Cash on Delivery";
            case "PENDING" -> "⏳ Payment Pending";
            default -> status;
        };
    }

    private String buildStatusBar(String status) {
        String s = status != null ? status.toUpperCase() : "PLACED";
        return switch (s) {
            case "PLACED" -> "🟢 *PLACED* ➔ ⚪ Baking ➔ ⚪ Out for Delivery ➔ ⚪ Delivered\n_(Your order is received and queued)_";
            case "CONFIRMED", "BAKING" -> "🟢 Placed ➔ 🟡 *BAKING / PREPARING* ➔ ⚪ Out for Delivery ➔ ⚪ Delivered\n_(Chef is preparing your fresh bakery items! 🍰)_";
            case "OUT_FOR_DELIVERY" -> "🟢 Placed ➔ 🟢 Baked ➔ 🔵 *OUT FOR DELIVERY* ➔ ⚪ Delivered\n_(Delivery partner is on the way to your address! 🛵)_";
            case "DELIVERED" -> "🟢 Placed ➔ 🟢 Baked ➔ 🟢 Out ➔ 🟣 *DELIVERED* 🎉\n_(Delivered! Enjoy your delicious treat!)_";
            case "CANCELLED" -> "🔴 *ORDER CANCELLED*\n_(Please contact support if you have questions)_";
            default -> "Status: " + status;
        };
    }
}
