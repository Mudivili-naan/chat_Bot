package whatsapp.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import whatsapp.bot.service.ChatbotFlowService;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    @Value("${whatsapp.verify-token:bakery_bot_secret_token}")
    private String verifyToken;

    private final ChatbotFlowService chatbotFlowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Webhook verification for Meta WhatsApp Cloud API setup
     */
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        log.info("Webhook verification request: mode={}, token={}", mode, token);
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch");
    }

    /**
     * Webhook receiving incoming WhatsApp user interactions
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveMessage(@RequestBody String payload) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            JsonNode entryArray = rootNode.path("entry");

            if (!entryArray.isArray() || entryArray.isEmpty()) {
                return ResponseEntity.ok().build();
            }

            JsonNode changesArray = entryArray.get(0).path("changes");
            if (!changesArray.isArray() || changesArray.isEmpty()) {
                return ResponseEntity.ok().build();
            }

            JsonNode valueNode = changesArray.get(0).path("value");

            if (valueNode.has("messages")) {
                JsonNode messageNode = valueNode.path("messages").get(0);
                String fromNumber = messageNode.path("from").asText();
                String messageType = messageNode.path("type").asText();

                // 1. Text Message
                if ("text".equals(messageType)) {
                    String userText = messageNode.path("text").path("body").asText();
                    chatbotFlowService.handleTextMessage(fromNumber, userText);
                }
                // 2. Interactive message (Button click or List dropdown item selected)
                else if ("interactive".equals(messageType)) {
                    JsonNode interactiveNode = messageNode.path("interactive");
                    String interactionType = interactiveNode.path("type").asText();

                    String selectedId = "";
                    if ("button_reply".equals(interactionType)) {
                        selectedId = interactiveNode.path("button_reply").path("id").asText();
                    } else if ("list_reply".equals(interactionType)) {
                        selectedId = interactiveNode.path("list_reply").path("id").asText();
                    }

                    if (!selectedId.isBlank()) {
                        chatbotFlowService.handleInteractiveSelection(fromNumber, selectedId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing incoming WhatsApp webhook payload", e);
        }

        return ResponseEntity.ok().build();
    }
}
