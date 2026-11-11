package whatsapp.bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import whatsapp.bot.entity.Product;
import whatsapp.bot.entity.ProductVariant;
import whatsapp.bot.repository.ProductRepository;
import whatsapp.bot.repository.ProductVariantRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    @Transactional(readOnly = true)
    public List<String> getAvailableCategories() {
        return productRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getCategoryRows() {
        List<String> categories = getAvailableCategories();
        List<Map<String, String>> rows = new ArrayList<>();
        for (String cat : categories) {
            Map<String, String> row = new HashMap<>();
            row.put("id", "CAT_" + cat);
            row.put("title", formatCategoryTitle(cat));
            row.put("description", "Explore fresh " + cat.toLowerCase() + " items");
            rows.add(row);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getProductRowsByCategory(String category) {
        List<Product> products = productRepository.findByCategoryWithVariants(category);
        List<Map<String, String>> rows = new ArrayList<>();
        for (Product product : products) {
            Map<String, String> row = new HashMap<>();
            row.put("id", "PROD_" + product.getId());
            row.put("title", truncate(product.getName(), 24));
            String desc = product.getDescription() != null ? product.getDescription() : "Freshly baked";
            row.put("description", truncate(desc, 70));
            rows.add(row);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getVariantRowsByProduct(Long productId) {
        Optional<Product> productOpt = productRepository.findByIdWithVariants(productId);
        List<Map<String, String>> rows = new ArrayList<>();
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            for (ProductVariant variant : product.getVariants()) {
                if (Boolean.TRUE.equals(variant.getIsActive())) {
                    Map<String, String> row = new HashMap<>();
                    row.put("id", "VAR_" + variant.getId());
                    row.put("title", truncate(variant.getVariantName() + " - ₹" + variant.getPrice(), 24));
                    row.put("description", "Select " + variant.getVariantName() + " for " + product.getName());
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getActiveMenuList() {
        List<Map<String, String>> menuItems = new ArrayList<>();
        productRepository.findAll().forEach(product -> {
            if (Boolean.TRUE.equals(product.getIsActive()) && product.getVariants() != null) {
                product.getVariants().forEach(variant -> {
                    if (Boolean.TRUE.equals(variant.getIsActive())) {
                        Map<String, String> item = new HashMap<>();
                        item.put("id", "VAR_" + variant.getId());
                        item.put("title", truncate(product.getName(), 24));
                        item.put("description", truncate(variant.getVariantName() + " - ₹" + variant.getPrice(), 70));
                        menuItems.add(item);
                    }
                });
            }
        });
        return menuItems;
    }

    private String formatCategoryTitle(String cat) {
        return switch (cat.toUpperCase()) {
            case "CAKE" -> "🎂 Cakes & Pastries";
            case "PASTRY" -> "🥐 Crispy Pastries";
            case "SAVOURY" -> "🥟 Savoury & Puffs";
            case "BREAD" -> "🍞 Fresh Breads & Loaves";
            default -> "🍰 " + cat.substring(0, 1).toUpperCase() + cat.substring(1).toLowerCase();
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }
}
