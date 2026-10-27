package com.example.dating_application.Websocket;

import com.example.dating_application.DTO.Response.IslandGameDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Кожен гравець отримує власний вигляд партії: спільний payload видав би чужий хід. */
@Service
public class IslandRealtimeNotifier {

    public static final String ISLAND_UPDATED = "ISLAND_UPDATED";

    private static final Logger logger = LoggerFactory.getLogger(IslandRealtimeNotifier.class);

    private final ChatWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public IslandRealtimeNotifier(ChatWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void notifyParticipant(Long userId, IslandGameDTO view) {
        try {
            handler.sendToUser(userId, objectMapper.writeValueAsString(new WsEvent(ISLAND_UPDATED, view)));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize island WS event", e);
        }
    }
}
