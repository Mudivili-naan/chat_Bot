package whatsapp.bot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import whatsapp.bot.model.BotStep;
import whatsapp.bot.model.UserSession;

import java.time.Duration;

@Component
public class SessionManager {

    // 30-minute conversation window per user
    private final Cache<String, UserSession> sessionCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10000)
            .build();

    public UserSession getSession(String phoneNumber) {
        return sessionCache.get(phoneNumber, phone -> UserSession.builder()
                .phoneNumber(phone)
                .step(BotStep.MAIN_MENU)
                .quantity(1)
                .build());
    }

    public void updateSession(String phoneNumber, UserSession session) {
        sessionCache.put(phoneNumber, session);
    }

    public void clearSession(String phoneNumber) {
        sessionCache.invalidate(phoneNumber);
    }
}
