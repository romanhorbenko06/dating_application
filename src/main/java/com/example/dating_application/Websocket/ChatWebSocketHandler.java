package com.example.dating_application.Websocket;

import com.example.dating_application.Security.JwtHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    /** Менше за типові 30–60 с, після яких проксі рве неактивне з'єднання. */
    private static final long PING_INTERVAL_MS = 25_000L;

    /** Без цього сокет після обриву без FIN (сон ноутбука, зникнення Wi-Fi) висів би в реєстрі вічно. */
    private static final long DEFAULT_PONG_TIMEOUT_MS = 70_000L;

    /** Параметр, а не константа, щоб тест не чекав 70 секунд. */
    private final long pongTimeoutMs;

    /** Браузер не вміє слати ping-фрейми з JS, тому є прикладний ping. */
    private static final TextMessage PONG_EVENT = new TextMessage("{\"type\":\"PONG\"}");

    private static final class SessionHolder {
        private final WebSocketSession session;
        private volatile long lastSeenAt;

        private SessionHolder(WebSocketSession session) {
            this.session = session;
            this.lastSeenAt = System.currentTimeMillis();
        }

        private void touch() {
            this.lastSeenAt = System.currentTimeMillis();
        }
    }

    private final Map<Long, Map<String, SessionHolder>> sessionsByUser = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            @Value("${app.ws.pong-timeout-ms:" + DEFAULT_PONG_TIMEOUT_MS + "}") long pongTimeoutMs) {
        this.pongTimeoutMs = pongTimeoutMs;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        WebSocketSession concurrent =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);

        // compute, щоб додавання було атомарним щодо прибирання порожньої мапи.
        sessionsByUser.compute(userId, (id, sessions) -> {
            Map<String, SessionHolder> map = (sessions == null) ? new ConcurrentHashMap<>() : sessions;
            map.put(session.getId(), new SessionHolder(concurrent));
            return map;
        });

        logger.debug("WS connected: user={} sessions={}", userId, sessionsByUser.get(userId).size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Повідомлення йдуть через REST; тут приймаємо лише прикладний ping.
        SessionHolder holder = holderOf(session);
        if (holder != null) {
            holder.touch();
        }
        if (!isClientPing(message.getPayload())) {
            return;
        }
        try {
            (holder != null ? holder.session : session).sendMessage(PONG_EVENT);
        } catch (IOException e) {
            logger.debug("Failed to answer client ping", e);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        SessionHolder holder = holderOf(session);
        if (holder != null) {
            holder.touch();
        }
    }

    @Scheduled(fixedDelay = PING_INTERVAL_MS, initialDelay = PING_INTERVAL_MS)
    public void heartbeat() {
        long now = System.currentTimeMillis();
        PingMessage ping = new PingMessage();

        for (Map.Entry<Long, Map<String, SessionHolder>> entry : sessionsByUser.entrySet()) {
            Long userId = entry.getKey();
            Map<String, SessionHolder> sessions = entry.getValue();

            for (Map.Entry<String, SessionHolder> sessionEntry : sessions.entrySet()) {
                SessionHolder holder = sessionEntry.getValue();
                WebSocketSession session = holder.session;

                if (!session.isOpen()) {
                    sessions.remove(sessionEntry.getKey());
                    continue;
                }
                if (now - holder.lastSeenAt > pongTimeoutMs) {
                    logger.debug("WS session of user {} is stale ({} ms without pong), closing",
                            userId, now - holder.lastSeenAt);
                    sessions.remove(sessionEntry.getKey());
                    closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
                    continue;
                }
                try {
                    session.sendMessage(ping);
                } catch (IOException | IllegalStateException e) {
                    logger.debug("Failed to ping WS session of user {}, closing", userId, e);
                    sessions.remove(sessionEntry.getKey());
                    closeQuietly(session, CloseStatus.SERVER_ERROR);
                }
            }

            sessionsByUser.computeIfPresent(userId, (id, map) -> map.isEmpty() ? null : map);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return;
        }
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public void sendToUser(Long userId, String payload) {
        Map<String, SessionHolder> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (SessionHolder holder : sessions.values()) {
            WebSocketSession session = holder.session;
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                logger.warn("Failed to push WS message to user {}, closing session", userId, e);
                closeQuietly(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    public void disconnectUser(Long userId) {
        Map<String, SessionHolder> sessions = sessionsByUser.remove(userId);
        if (sessions == null) {
            return;
        }
        for (SessionHolder holder : sessions.values()) {
            try {
                holder.session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                logger.warn("Failed to close WS session of blocked user {}", userId, e);
            }
        }
        logger.info("Closed {} WS session(s) of blocked user {}", sessions.size(), userId);
    }

    public void disconnectToken(Long userId, String jti) {
        if (jti == null) {
            return;
        }
        Map<String, SessionHolder> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        int closed = 0;
        for (SessionHolder holder : sessions.values()) {
            WebSocketSession session = holder.session;
            if (!jti.equals(session.getAttributes().get(JwtHandshakeInterceptor.JTI_ATTR))) {
                continue;
            }
            try {
                session.close(CloseStatus.NORMAL);
                closed++;
            } catch (IOException e) {
                logger.warn("Failed to close WS session on logout of user {}", userId, e);
            }
        }
        if (closed > 0) {
            logger.debug("Closed {} WS session(s) on logout of user {}", closed, userId);
        }
    }

    private SessionHolder holderOf(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return null;
        }
        Map<String, SessionHolder> sessions = sessionsByUser.get(userId);
        return sessions == null ? null : sessions.get(session.getId());
    }

    private boolean isClientPing(String payload) {
        if (payload == null || payload.length() > 64) {
            return false;
        }
        String normalized = payload.trim();
        return normalized.equalsIgnoreCase("ping")
                || normalized.replace(" ", "").equalsIgnoreCase("{\"type\":\"PING\"}");
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
        }
    }
}
