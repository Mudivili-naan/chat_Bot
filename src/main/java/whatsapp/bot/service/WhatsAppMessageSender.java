package whatsapp.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WhatsAppMessageSender {

    @Value("${whatsapp.phone-number-id:1341058652432056}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.api-version:v21.0}")
    private String apiVersion;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getApiUrl() {
        return "https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages";
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isBlank()) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }

    /**
     * Send standard WhatsApp text message
     */
    public void sendTextMessage(String recipientPhone, String messageText) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", recipientPhone);
        body.put("type", "text");
        body.put("text", Map.of("body", messageText, "preview_url", true));

        postPayload(recipientPhone, body, "text message");
    }

    /**
     * Send WhatsApp Quick Reply Buttons (up to 3 buttons)
     */
    public void sendInteractiveButtons(String recipientPhone, String headerText, String bodyText, Map<String, String> buttons) {
        var buttonList = new ArrayList<Map<String, Object>>();
        buttons.forEach((id, title) -> {
            Map<String, Object> reply = new HashMap<>();
            reply.put("id", id);
            reply.put("title", title.length() > 20 ? title.substring(0, 20) : title);

            Map<String, Object> buttonItem = new HashMap<>();
            buttonItem.put("type", "reply");
            buttonItem.put("reply", reply);
            buttonList.add(buttonItem);
        });

        Map<String, Object> action = new HashMap<>();
        action.put("buttons", buttonList);

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "button");
        if (headerText != null && !headerText.isBlank()) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }
        interactive.put("body", Map.of("text", bodyText));
        interactive.put("action", action);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", recipientPhone);
        payload.put("type", "interactive");
        payload.put("interactive", interactive);

        postPayload(recipientPhone, payload, "interactive buttons");
    }

    /**
     * Send WhatsApp Dropdown List (Interactive List)
     */
    public void sendInteractiveList(String recipientPhone, String bodyText, String buttonLabel, List<Map<String, String>> items) {
        var rows = new ArrayList<Map<String, Object>>();
        for (Map<String, String> item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", item.get("id"));
            String title = item.get("title");
            row.put("title", title != null && title.length() > 24 ? title.substring(0, 24) : title);
            if (item.containsKey("description") && item.get("description") != null) {
                String desc = item.get("description");
                row.put("description", desc.length() > 72 ? desc.substring(0, 72) : desc);
            }
            rows.add(row);
        }

        Map<String, Object> section = new HashMap<>();
        section.put("title", "Select an Item");
        section.put("rows", rows);

        Map<String, Object> action = new HashMap<>();
        action.put("button", buttonLabel.length() > 20 ? buttonLabel.substring(0, 20) : buttonLabel);
        action.put("sections", List.of(section));

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "list");
        interactive.put("body", Map.of("text", bodyText));
        interactive.put("action", action);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", recipientPhone);
        payload.put("type", "interactive");
        payload.put("interactive", interactive);

        postPayload(recipientPhone, payload, "interactive list");
    }

    /**
     * Send WhatsApp CTA URL Button (Call To Action button for Payment Link)
     */
    public void sendInteractiveCtaUrl(String recipientPhone, String headerText, String bodyText, String buttonText, String targetUrl) {
        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("display_text", buttonText.length() > 20 ? buttonText.substring(0, 20) : buttonText);
            parameters.put("url", targetUrl);

            Map<String, Object> action = new HashMap<>();
            action.put("name", "cta_url");
            action.put("parameters", parameters);

            Map<String, Object> interactive = new HashMap<>();
            interactive.put("type", "cta_url");
            if (headerText != null && !headerText.isBlank()) {
                interactive.put("header", Map.of("type", "text", "text", headerText));
            }
            interactive.put("body", Map.of("text", bodyText));
            interactive.put("action", action);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", recipientPhone);
            payload.put("type", "interactive");
            payload.put("interactive", interactive);

            postPayload(recipientPhone, payload, "interactive CTA URL");
        } catch (Exception e) {
            log.warn("CTA URL send failed, falling back to text with link: {}", e.getMessage());
            sendTextMessage(recipientPhone, bodyText + "\n\n👉 Pay Here: " + targetUrl);
        }
    }

    private void postPayload(String recipientPhone, Map<String, Object> payload, String messageType) {
        try {
            var entity = new HttpEntity<>(payload, createHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(getApiUrl(), entity, String.class);
            log.info("Sent {} successfully to {}. Status: {}", messageType, recipientPhone, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to send {} to {}: {}", messageType, recipientPhone, e.getMessage());
        }
    }
}
