package com.example.dating_application.Websocket;

import com.example.dating_application.DTO.Response.MessageResponseDTO;
import com.example.dating_application.DTO.Response.ReadReceiptDTO;
import com.example.dating_application.Entity.Chat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Пушить нове повідомлення обом учасникам чату через WebSocket.
 * Викликається ПІСЛЯ збереження повідомлення в БД (консистентність:
 * БД — джерело правди, WebSocket — лише сповіщення).
 */
@Service
public class ChatRealtimeNotifier {

    private static final Logger logger = LoggerFactory.getLogger(ChatRealtimeNotifier.class);

    private final ChatWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public ChatRealtimeNotifier(ChatWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    /** Пуш нового повідомлення обом учасникам. */
    public void notifyNewMessage(Chat chat, MessageResponseDTO message) {
        broadcast(chat, new WsEvent("NEW_MESSAGE", message));
    }

    /** Пуш відредагованого повідомлення обом учасникам (FR-17.3). */
    public void notifyMessageEdited(Chat chat, MessageResponseDTO message) {
        broadcast(chat, new WsEvent("MESSAGE_EDITED", message));
    }

    /** Пуш видалення повідомлення обом учасникам (FR-17.4); у payload content уже null. */
    public void notifyMessageDeleted(Chat chat, MessageResponseDTO message) {
        broadcast(chat, new WsEvent("MESSAGE_DELETED", message));
    }

    /** Пуш квитанції прочитання обом учасникам (відправник бачить «прочитано», інші девайси синхронізуються). */
    public void notifyReadReceipt(Chat chat, Long readerId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        broadcast(chat, new WsEvent("READ_RECEIPT", new ReadReceiptDTO(chat.getChatId(), readerId, messageIds)));
    }

    private void broadcast(Chat chat, WsEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize WS event payload", e);
            return;
        }
        handler.sendToUser(chat.getUser1().getUserId(), payload);
        handler.sendToUser(chat.getUser2().getUserId(), payload);
    }
}