package com.example.dating_application.Websocket;

import com.example.dating_application.DTO.Response.NotificationResponseDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class NotificationRealtimeNotifier {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRealtimeNotifier.class);

    private final ChatWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public NotificationRealtimeNotifier(ChatWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void notifyRecipient(Long recipientId, NotificationResponseDTO notification) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new WsEvent("NOTIFICATION", notification));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize notification WS payload", e);
            return;
        }
        handler.sendToUser(recipientId, payload);
    }
}
