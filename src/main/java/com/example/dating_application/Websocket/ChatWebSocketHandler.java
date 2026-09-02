package com.example.dating_application.Websocket;

import com.example.dating_application.Security.JwtHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
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

    private final Map<Long, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        WebSocketSession concurrent =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);

        sessionsByUser
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), concurrent);

        logger.debug("WS connected: user={} sessions={}", userId, sessionsByUser.get(userId).size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Навмисно НЕ обробляємо вхідні фрейми: запис іде через REST
        // (POST /api/chats/messages) для консистентності (спершу БД, потім пуш).
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

    /** Надсилає готовий JSON-текст усім відкритим сесіям користувача. */
    public void sendToUser(Long userId, String payload) {
        Map<String, WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                logger.warn("Failed to push WS message to user {}, closing session", userId, e);
                closeQuietly(session);
            }
        }
    }


    public void disconnectUser(Long userId) {
        Map<String, WebSocketSession> sessions = sessionsByUser.remove(userId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions.values()) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
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
        Map<String, WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        int closed = 0;
        for (WebSocketSession session : sessions.values()) {
            if (!jti.equals(session.getAttributes().get(JwtHandshakeInterceptor.JTI_ATTR))) {
                continue;
            }
            try {
                // NORMAL, а не POLICY_VIOLATION: це штатний вихід, не санкція
                session.close(CloseStatus.NORMAL);
                closed++;
            } catch (IOException e) {
                logger.warn("Failed to close WS session on logout of user {}", userId, e);
            }
        }
        // з реєстру сесії приберуться в afterConnectionClosed
        if (closed > 0) {
            logger.debug("Closed {} WS session(s) on logout of user {}", closed, userId);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // сесію все одно приберемо в afterConnectionClosed
        }
    }
}