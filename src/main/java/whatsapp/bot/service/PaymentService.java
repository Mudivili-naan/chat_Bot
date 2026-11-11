package whatsapp.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import whatsapp.bot.entity.Order;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${payment.upi.vpa:bakery@upi}")
    private String upiVpa;

    @Value("${payment.upi.name:Sweet Delights Bakery}")
    private String businessName;

    @Value("${payment.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${payment.razorpay.key-secret:}")
    private String razorpayKeySecret;

    /**
     * Generate standard UPI deep link (Works directly with PayZapp, GPay, PhonePe, Paytm)
     */
    public String generateUpiDeepLink(String orderNumber, BigDecimal amount) {
        try {
            String encodedName = URLEncoder.encode(businessName, StandardCharsets.UTF_8);
            String note = URLEncoder.encode("Payment for " + orderNumber, StandardCharsets.UTF_8);
            return String.format(
                    "upi://pay?pa=%s&pn=%s&am=%.2f&cu=INR&tn=%s&tr=%s",
                    upiVpa, encodedName, amount.doubleValue(), note, orderNumber
            );
        } catch (Exception e) {
            log.error("Failed to generate UPI deep link for {}", orderNumber, e);
            return "";
        }
    }

    /**
     * Generate hosted payment link (interactive web page supporting PayZapp, UPI, and Mock Card payment)
     */
    public String generatePaymentLink(Order order) {
        // Points to our application's interactive web checkout / payment page
        return appBaseUrl + "/payment/checkout/" + order.getOrderNumber();
    }
}
