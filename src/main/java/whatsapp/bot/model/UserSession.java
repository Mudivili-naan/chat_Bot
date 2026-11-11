package whatsapp.bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private BotStep step = BotStep.MAIN_MENU;

    private String phoneNumber;
    private String customerName;

    // Selected product & variant details
    private String selectedCategory;
    private Long selectedProductId;
    private Long selectedVariantId;
    private String productName;
    private String variantName;
    private BigDecimal unitPrice;

    @Builder.Default
    private Integer quantity = 1;

    private BigDecimal totalPrice;
    private String customNotes;
    private String deliveryAddress;

    // Active order being processed
    private String activeOrderNumber;
    private String paymentMethod; // UPI, PAYZAPP, COD
    private String paymentLinkId;

    public void calculateTotal() {
        if (unitPrice != null && quantity != null && quantity > 0) {
            this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public void resetOrderDraft() {
        this.step = BotStep.MAIN_MENU;
        this.selectedCategory = null;
        this.selectedProductId = null;
        this.selectedVariantId = null;
        this.productName = null;
        this.variantName = null;
        this.unitPrice = null;
        this.quantity = 1;
        this.totalPrice = null;
        this.customNotes = null;
        this.activeOrderNumber = null;
        this.paymentMethod = null;
        this.paymentLinkId = null;
    }
}
