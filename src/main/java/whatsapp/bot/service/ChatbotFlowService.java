package whatsapp.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import whatsapp.bot.entity.Customer;
import whatsapp.bot.entity.Order;
import whatsapp.bot.entity.Product;
import whatsapp.bot.entity.ProductVariant;
import whatsapp.bot.model.BotStep;
import whatsapp.bot.model.UserSession;
import whatsapp.bot.repository.CustomerRepository;
import whatsapp.bot.repository.ProductRepository;
import whatsapp.bot.repository.ProductVariantRepository;
import whatsapp.bot.session.SessionManager;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotFlowService {

    private final SessionManager sessionManager;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final WhatsAppMessageSender messageSender;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;

    /**
     * Handle incoming text message
     */
    public void handleTextMessage(String fromNumber, String text) {
        String trimmed = text != null ? text.trim() : "";
        String upper = trimmed.toUpperCase();

        UserSession session = sessionManager.getSession(fromNumber);

        // Global commands check
        if (upper.equals("HI") || upper.equals("HELLO") || upper.equals("START") || upper.equals("VANAKKAM") || upper.equals("MENU")) {
            session.resetOrderDraft();
            sessionManager.updateSession(fromNumber, session);
            sendMainMenu(fromNumber);
            return;
        }

        if (upper.equals("CANCEL") || upper.equals("RESET")) {
            session.resetOrderDraft();
            sessionManager.updateSession(fromNumber, session);
            messageSender.sendTextMessage(fromNumber, "❌ Your current selection has been cleared.");
            sendMainMenu(fromNumber);
            return;
        }

        if (upper.equals("TRACK") || upper.startsWith("TRACK ")) {
            String code = upper.replace("TRACK", "").trim();
            if (!code.isEmpty()) {
                handleOrderTrackingCode(fromNumber, code);
            } else {
                promptOrderTracking(fromNumber);
            }
            return;
        }

        if (upper.startsWith("ORD-")) {
            handleOrderTrackingCode(fromNumber, upper);
            return;
        }

        // State Machine Handling for Text Inputs
        switch (session.getStep()) {
            case SELECT_QUANTITY -> {
                try {
                    int qty = Integer.parseInt(trimmed);
                    if (qty <= 0 || qty > 50) {
                        messageSender.sendTextMessage(fromNumber, "⚠️ Please enter a valid quantity between 1 and 50:");
                        return;
                    }
                    session.setQuantity(qty);
                    session.calculateTotal();
                    session.setStep(BotStep.CUSTOM_NOTES);
                    sessionManager.updateSession(fromNumber, session);
                    promptCustomNotes(fromNumber, session);
                } catch (NumberFormatException e) {
                    messageSender.sendTextMessage(fromNumber, "⚠️ Please enter a number for quantity (e.g. 1, 2, 3):");
                }
            }

            case CUSTOM_NOTES -> {
                session.setCustomNotes(trimmed);
                session.setStep(BotStep.AWAITING_ADDRESS);
                sessionManager.updateSession(fromNumber, session);
                promptDeliveryAddress(fromNumber, session);
            }

            case AWAITING_ADDRESS -> {
                session.setDeliveryAddress(trimmed);
                session.setStep(BotStep.CONFIRM_ORDER);
                sessionManager.updateSession(fromNumber, session);
                sendOrderConfirmationReview(fromNumber, session);
            }

            case AWAITING_TRACKING_CODE -> {
                handleOrderTrackingCode(fromNumber, upper);
            }

            default -> {
                sendMainMenu(fromNumber);
            }
        }
    }

    /**
     * Handle incoming interactive selection (Button reply or List row click)
     */
    public void handleInteractiveSelection(String fromNumber, String selectedId) {
        UserSession session = sessionManager.getSession(fromNumber);
        log.info("Interactive selection: id={} for phone={}", selectedId, fromNumber);

        // 1. Main Navigation Buttons
        if ("BTN_MAIN_MENU".equals(selectedId)) {
            session.resetOrderDraft();
            sessionManager.updateSession(fromNumber, session);
            sendMainMenu(fromNumber);
            return;
        }

        if ("BTN_BROWSE_CAT".equals(selectedId)) {
            sendCategoriesList(fromNumber);
            return;
        }

        if ("BTN_TRACK_ORDER".equals(selectedId)) {
            promptOrderTracking(fromNumber);
            return;
        }

        if ("BTN_HELP".equals(selectedId)) {
            sendHelpMessage(fromNumber);
            return;
        }

        // 2. Category Click (CAT_...)
        if (selectedId.startsWith("CAT_")) {
            String category = selectedId.replace("CAT_", "");
            session.setSelectedCategory(category);
            session.setStep(BotStep.SELECT_PRODUCT);
            sessionManager.updateSession(fromNumber, session);

            List<Map<String, String>> products = catalogService.getProductRowsByCategory(category);
            if (products.isEmpty()) {
                messageSender.sendTextMessage(fromNumber, "No items found in this category right now.");
                sendMainMenu(fromNumber);
            } else {
                messageSender.sendInteractiveList(
                        fromNumber,
                        "Here are our freshly prepared " + category.toLowerCase() + " items. Tap below to pick one:",
                        "View " + category,
                        products
                );
            }
            return;
        }

        // 3. Product Click (PROD_...)
        if (selectedId.startsWith("PROD_")) {
            Long productId = Long.parseLong(selectedId.replace("PROD_", ""));
            List<Map<String, String>> variants = catalogService.getVariantRowsByProduct(productId);

            if (variants.isEmpty()) {
                messageSender.sendTextMessage(fromNumber, "Sorry, this product is currently sold out.");
                sendMainMenu(fromNumber);
                return;
            }

            if (variants.size() == 1) {
                // If only 1 variant, auto-select it directly
                handleVariantSelection(fromNumber, session, variants.get(0).get("id"));
            } else {
                messageSender.sendInteractiveList(
                        fromNumber,
                        "Please choose size / weight:",
                        "Select Variant",
                        variants
                );
            }
            return;
        }

        // 4. Variant Click (VAR_...)
        if (selectedId.startsWith("VAR_")) {
            handleVariantSelection(fromNumber, session, selectedId);
            return;
        }

        // 5. Quantity Selection (QTY_1, QTY_2, QTY_3)
        if (selectedId.startsWith("QTY_")) {
            int qty = Integer.parseInt(selectedId.replace("QTY_", ""));
            session.setQuantity(qty);
            session.calculateTotal();
            session.setStep(BotStep.CUSTOM_NOTES);
            sessionManager.updateSession(fromNumber, session);
            promptCustomNotes(fromNumber, session);
            return;
        }

        // 6. Custom Notes Action (Skip)
        if ("NOTE_SKIP".equals(selectedId)) {
            session.setCustomNotes("None");
            session.setStep(BotStep.AWAITING_ADDRESS);
            sessionManager.updateSession(fromNumber, session);
            promptDeliveryAddress(fromNumber, session);
            return;
        }

        // 7. Address Selection (Use Saved)
        if ("ADDR_USE_SAVED".equals(selectedId)) {
            Customer c = customerRepository.findById(fromNumber).orElse(null);
            if (c != null && c.getDeliveryAddress() != null) {
                session.setDeliveryAddress(c.getDeliveryAddress());
                session.setStep(BotStep.CONFIRM_ORDER);
                sessionManager.updateSession(fromNumber, session);
                sendOrderConfirmationReview(fromNumber, session);
                return;
            }
            promptDeliveryAddress(fromNumber, session);
            return;
        }

        // 8. Order Payment Modes
        if ("PAY_ONLINE".equals(selectedId)) {
            session.setPaymentMethod("ONLINE");
            Order order = orderService.placeOrder(session);
            String paymentUrl = paymentService.generatePaymentLink(order);

            String bodyText = "🎉 *Order Placed: " + order.getOrderNumber() + "*\n\n"
                    + "Item: " + session.getProductName() + " (" + session.getVariantName() + ") x " + session.getQuantity() + "\n"
                    + "Total: *₹" + order.getTotalAmount() + "*\n\n"
                    + "Tap the button below to complete payment instantly via PayZapp or UPI:";

            // Send interactive CTA URL or fallback
            messageSender.sendInteractiveCtaUrl(
                    fromNumber,
                    "PayZapp / UPI Payment",
                    bodyText,
                    "Pay ₹" + order.getTotalAmount(),
                    paymentUrl
            );

            session.resetOrderDraft();
            sessionManager.updateSession(fromNumber, session);
            return;
        }

        if ("PAY_COD".equals(selectedId)) {
            session.setPaymentMethod("COD");
            Order order = orderService.placeOrder(session);

            String msg = "🎉 *Order Confirmed (Cash on Delivery)!*\n\n"
                    + "Order ID: *" + order.getOrderNumber() + "*\n"
                    + "Item: " + session.getProductName() + " (" + session.getVariantName() + ") x " + session.getQuantity() + "\n"
                    + "Total Amount: *₹" + order.getTotalAmount() + "*\n"
                    + "Payment: *Cash on Delivery*\n"
                    + "Delivery To: " + order.getDeliveryAddress() + "\n\n"
                    + "Our bakers are preparing your order! 👨‍🍳🎂\n"
                    + "Reply *TRACK* anytime to check live delivery status.";

            messageSender.sendTextMessage(fromNumber, msg);

            session.resetOrderDraft();
            sessionManager.updateSession(fromNumber, session);
            return;
        }

        if ("ORDER_CANCEL".equals(selectedId)) {
            session.resetOrderDraft();
            sessionManager.updateSession(fromNumber, session);
            messageSender.sendTextMessage(fromNumber, "❌ Order cancelled.");
            sendMainMenu(fromNumber);
            return;
        }

        // 9. Quick Track Recent Order
        if (selectedId.startsWith("TRACK_ORD_")) {
            String orderCode = selectedId.replace("TRACK_ORD_", "");
            handleOrderTrackingCode(fromNumber, orderCode);
            return;
        }

        // Fallback
        sendMainMenu(fromNumber);
    }

    private void handleVariantSelection(String fromNumber, UserSession session, String variantIdString) {
        Long variantId = Long.parseLong(variantIdString.replace("VAR_", ""));
        ProductVariant variant = variantRepository.findByIdWithProduct(variantId).orElse(null);

        if (variant == null) {
            messageSender.sendTextMessage(fromNumber, "Sorry, this variant is no longer available.");
            sendMainMenu(fromNumber);
            return;
        }

        session.setSelectedVariantId(variant.getId());
        session.setSelectedProductId(variant.getProduct().getId());
        session.setProductName(variant.getProduct().getName());
        session.setVariantName(variant.getVariantName());
        session.setUnitPrice(variant.getPrice());
        session.setQuantity(1);
        session.calculateTotal();
        session.setStep(BotStep.SELECT_QUANTITY);
        sessionManager.updateSession(fromNumber, session);

        Map<String, String> qtyButtons = new LinkedHashMap<>();
        qtyButtons.put("QTY_1", "1 Item");
        qtyButtons.put("QTY_2", "2 Items");
        qtyButtons.put("QTY_3", "3 Items");

        String msg = "You selected: *" + session.getProductName() + "* (" + session.getVariantName() + ")\n"
                + "Price: ₹" + session.getUnitPrice() + "\n\n"
                + "Please choose quantity below (or reply with a number):";

        messageSender.sendInteractiveButtons(fromNumber, "Select Quantity", msg, qtyButtons);
    }

    private void promptCustomNotes(String fromNumber, UserSession session) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("NOTE_SKIP", "Skip Note ➡️");
        buttons.put("BTN_MAIN_MENU", "Main Menu 🏠");

        String msg = "Item: *" + session.getProductName() + "* (Qty: " + session.getQuantity() + ")\n\n"
                + "Any special message or instruction? (e.g., 'Happy Birthday Ananya', 'Eggless', 'Less sweet')\n\n"
                + "Reply with your note, or click *Skip Note* below:";

        messageSender.sendInteractiveButtons(fromNumber, "Special Note", msg, buttons);
    }

    private void promptDeliveryAddress(String fromNumber, UserSession session) {
        Customer customer = customerRepository.findById(fromNumber).orElse(null);
        if (customer != null && customer.getDeliveryAddress() != null && !customer.getDeliveryAddress().isBlank()) {
            Map<String, String> buttons = new LinkedHashMap<>();
            buttons.put("ADDR_USE_SAVED", "Use Saved Address");
            buttons.put("BTN_MAIN_MENU", "Cancel ❌");

            String msg = "Deliver to your saved address?\n\n📍 *" + customer.getDeliveryAddress() + "*\n\n"
                    + "Click *Use Saved Address*, or type a new address below:";
            messageSender.sendInteractiveButtons(fromNumber, "Delivery Address", msg, buttons);
        } else {
            messageSender.sendTextMessage(fromNumber, "📍 Please reply with your full Delivery Address (Flat/Door no, Street, Area, Pincode):");
        }
    }

    private void sendOrderConfirmationReview(String fromNumber, UserSession session) {
        session.calculateTotal();

        StringBuilder sb = new StringBuilder();
        sb.append("🧾 *ORDER CONFIRMATION REVIEW*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("• *Item:* ").append(session.getProductName()).append(" (").append(session.getVariantName()).append(")\n");
        sb.append("• *Qty:* ").append(session.getQuantity()).append("\n");
        sb.append("• *Unit Price:* ₹").append(session.getUnitPrice()).append("\n");
        if (session.getCustomNotes() != null && !session.getCustomNotes().equalsIgnoreCase("None")) {
            sb.append("• *Note:* ").append(session.getCustomNotes()).append("\n");
        }
        sb.append("• *Delivery To:* ").append(session.getDeliveryAddress()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("💰 *TOTAL PAYABLE: ₹").append(session.getTotalPrice()).append("*\n\n");
        sb.append("Select your payment method to confirm order:");

        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("PAY_ONLINE", "💳 PayZapp / UPI");
        buttons.put("PAY_COD", "💵 Cash on Delivery");
        buttons.put("ORDER_CANCEL", "❌ Cancel");

        messageSender.sendInteractiveButtons(fromNumber, "Confirm Order", sb.toString(), buttons);
    }

    private void sendMainMenu(String fromNumber) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("BTN_BROWSE_CAT", "Browse Menu 🍰");
        buttons.put("BTN_TRACK_ORDER", "Track Order 📦");
        buttons.put("BTN_HELP", "Help & FAQs 💬");

        String welcomeText = "Vanakkam! 🙏 Welcome to *Sweet Delights Bakery*.\n\n"
                + "Order fresh artisan cakes, pastries, savouries & breads with fast doorstep delivery.\n\n"
                + "Please choose an option to get started:";

        messageSender.sendInteractiveButtons(fromNumber, "Welcome", welcomeText, buttons);
    }

    private void sendCategoriesList(String fromNumber) {
        List<Map<String, String>> categories = catalogService.getCategoryRows();
        if (categories.isEmpty()) {
            messageSender.sendTextMessage(fromNumber, "Our menu is being updated right now. Please check back shortly!");
            return;
        }

        messageSender.sendInteractiveList(
                fromNumber,
                "Explore our fresh bakery categories. Select one to view delicious treats:",
                "Select Category",
                categories
        );
    }

    private void promptOrderTracking(String fromNumber) {
        Order latest = orderService.getLatestOrderByPhone(fromNumber);
        if (latest != null) {
            Map<String, String> buttons = new LinkedHashMap<>();
            buttons.put("TRACK_ORD_" + latest.getOrderNumber(), "Track " + latest.getOrderNumber());
            buttons.put("BTN_MAIN_MENU", "Main Menu 🏠");

            messageSender.sendInteractiveButtons(
                    fromNumber,
                    "Track Order",
                    "Would you like to track your recent order *" + latest.getOrderNumber() + "* or reply with another Order ID?",
                    buttons
            );
        } else {
            UserSession session = sessionManager.getSession(fromNumber);
            session.setStep(BotStep.AWAITING_TRACKING_CODE);
            sessionManager.updateSession(fromNumber, session);
            messageSender.sendTextMessage(fromNumber, "📦 Please reply with your 6-digit Order ID (e.g. *ORD-123456*):");
        }
    }

    private void handleOrderTrackingCode(String fromNumber, String orderCode) {
        Order order = orderService.getOrderByCode(orderCode);
        if (order != null) {
            String card = orderService.buildTrackingCard(order);
            Map<String, String> buttons = new LinkedHashMap<>();
            buttons.put("BTN_BROWSE_CAT", "Order More 🍰");
            buttons.put("BTN_MAIN_MENU", "Main Menu 🏠");

            messageSender.sendInteractiveButtons(fromNumber, "Order Status", card, buttons);
        } else {
            messageSender.sendTextMessage(fromNumber, "❌ No order found matching *" + orderCode + "*. Please double check your order number or type *MENU*.");
        }
    }

    private void sendHelpMessage(String fromNumber) {
        String help = "ℹ️ *Bakery Bot Support*\n\n"
                + "• *Browse Menu:* Type *MENU* anytime to view items\n"
                + "• *Track Order:* Type *TRACK ORD-xxxxxx*\n"
                + "• *Cancel:* Type *CANCEL* or *RESET* to restart\n"
                + "• *Phone Support:* +91 98765 43210\n"
                + "• *Operating Hours:* 8:00 AM - 10:00 PM\n\n"
                + "Enjoy your freshly baked treats! 🎂";
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("BTN_BROWSE_CAT", "Browse Menu 🍰");
        buttons.put("BTN_MAIN_MENU", "Main Menu 🏠");
        messageSender.sendInteractiveButtons(fromNumber, "Customer Support", help, buttons);
    }
}
