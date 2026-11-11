package whatsapp.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import whatsapp.bot.entity.Order;
import whatsapp.bot.service.OrderService;
import whatsapp.bot.service.PaymentService;
import whatsapp.bot.service.WhatsAppMessageSender;

import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final WhatsAppMessageSender messageSender;

    /**
     * Mobile-friendly payment checkout page (supporting PayZapp, UPI, and Test simulator)
     */
    @GetMapping(value = "/checkout/{orderNumber}", produces = "text/html;charset=UTF-8")
    public String checkoutPage(@PathVariable String orderNumber) {
        Order order = orderService.getOrderByCode(orderNumber);
        if (order == null) {
            return "<div style='font-family:sans-serif;text-align:center;padding:50px;'><h2>❌ Order Not Found</h2><p>Please check your order number.</p></div>";
        }

        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return "<div style='font-family:sans-serif;text-align:center;padding:50px;'>"
                    + "<h2 style='color:#10b981;'>✅ Order Already Paid!</h2>"
                    + "<p>Order <b>" + order.getOrderNumber() + "</b> is already confirmed.</p>"
                    + "<p>Total: ₹" + order.getTotalAmount() + "</p>"
                    + "</div>";
        }

        String upiLink = paymentService.generateUpiDeepLink(order.getOrderNumber(), order.getTotalAmount());

        return "<!DOCTYPE html>"
                + "<html lang='en'>"
                + "<head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>PayZapp / UPI Checkout - " + order.getOrderNumber() + "</title>"
                + "<style>"
                + "* { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }"
                + "body { background: #f3f4f6; margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 100vh; }"
                + ".card { background: white; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); max-width: 420px; width: 100%; padding: 28px; text-align: center; }"
                + ".logo { font-size: 40px; margin-bottom: 8px; }"
                + ".title { font-size: 20px; font-weight: 700; color: #111827; margin: 0 0 4px 0; }"
                + ".order-tag { font-size: 13px; color: #6b7280; margin-bottom: 20px; }"
                + ".amount-box { background: #eff6ff; border: 1px dashed #3b82f6; border-radius: 12px; padding: 16px; margin-bottom: 24px; }"
                + ".amount-label { font-size: 12px; color: #1d4ed8; font-weight: 600; text-transform: uppercase; }"
                + ".amount-val { font-size: 32px; color: #1e3a8a; font-weight: 800; margin: 4px 0 0 0; }"
                + ".btn { display: block; width: 100%; padding: 14px; border-radius: 10px; font-size: 15px; font-weight: 600; cursor: pointer; text-decoration: none; border: none; margin-bottom: 12px; transition: transform 0.1s ease; }"
                + ".btn:active { transform: scale(0.98); }"
                + ".btn-upi { background: #0070ba; color: white; }"
                + ".btn-payzapp { background: #004c8f; color: white; display: flex; align-items: center; justify-content: center; gap: 8px; }"
                + ".btn-sim { background: #10b981; color: white; }"
                + ".footer-note { font-size: 11px; color: #9ca3af; margin-top: 16px; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='card'>"
                + "<div class='logo'>🧁</div>"
                + "<h1 class='title'>Sweet Delights Bakery</h1>"
                + "<div class='order-tag'>Order #" + order.getOrderNumber() + "</div>"
                + "<div class='amount-box'>"
                + "<div class='amount-label'>Amount to Pay</div>"
                + "<div class='amount-val'>₹" + order.getTotalAmount() + "</div>"
                + "</div>"
                + "<a href='" + upiLink + "' class='btn btn-payzapp'>⚡ Pay with PayZapp / UPI</a>"
                + "<button onclick='simulatePayment()' class='btn btn-sim'>✅ Test Pay (Instant Sandbox)</button>"
                + "<div class='footer-note'>100% Secure Payment • Instant WhatsApp Confirmation</div>"
                + "</div>"
                + "<script>"
                + "function simulatePayment() {"
                + "  fetch('/payment/callback', {"
                + "    method: 'POST',"
                + "    headers: {'Content-Type': 'application/json'},"
                + "    body: JSON.stringify({orderNumber: '" + order.getOrderNumber() + "', status: 'PAID', method: 'PAYZAPP_UPI'})"
                + "  }).then(r => r.json()).then(data => {"
                + "    if (data.success) {"
                + "      alert('Payment Successful! Check your WhatsApp for confirmation.');"
                + "      window.location.reload();"
                + "    } else {"
                + "      alert('Error updating payment');"
                + "    }"
                + "  });"
                + "}"
                + "</script>"
                + "</body>"
                + "</html>";
    }

    /**
     * Webhook/Callback for Payment Confirmation
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> paymentCallback(@RequestBody Map<String, String> payload) {
        String orderNumber = payload.get("orderNumber");
        String status = payload.getOrDefault("status", "PAID");
        String method = payload.getOrDefault("method", "PAYZAPP_UPI");

        boolean updated = orderService.updatePaymentStatus(orderNumber, status, "PAY_" + System.currentTimeMillis());

        if (updated) {
            Order order = orderService.getOrderByCode(orderNumber);
            if (order != null && "PAID".equalsIgnoreCase(status)) {
                String confirmMsg = "🎉 *Payment Received Successfully!*\n\n"
                        + "Order ID: *" + order.getOrderNumber() + "*\n"
                        + "Amount Paid: *₹" + order.getTotalAmount() + "*\n"
                        + "Payment Mode: " + method + "\n\n"
                        + "Chef has started baking your order fresh! 👨‍🍳🍰\n"
                        + "Reply *TRACK* at any time to see live updates.";
                messageSender.sendTextMessage(order.getCustomer().getPhoneNumber(), confirmMsg);
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Payment processed"));
        }

        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Order not found"));
    }
}
