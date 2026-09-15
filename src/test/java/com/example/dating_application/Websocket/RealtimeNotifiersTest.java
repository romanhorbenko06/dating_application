package com.example.dating_application.Websocket;

import com.example.dating_application.DTO.Response.MessageResponseDTO;
import com.example.dating_application.DTO.Response.NotificationResponseDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.NotificationType;
import com.example.dating_application.Entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Пуш подій у сокет.
 *
 * Правило одне й просте: подія йде ОБОМ учасникам чату і в тому вигляді, який
 * розбирає фронт — {type, payload}. Помилка тут виглядає як «повідомлення
 * дійшло одному, а другий сидить і не бачить».
 */
class RealtimeNotifiersTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private ChatWebSocketHandler handler;
    private ChatRealtimeNotifier chatNotifier;
    private NotificationRealtimeNotifier notificationNotifier;

    private Chat chat;

    @BeforeEach
    void setUp() {
        handler = mock(ChatWebSocketHandler.class);
        chatNotifier = new ChatRealtimeNotifier(handler, json);
        notificationNotifier = new NotificationRealtimeNotifier(handler, json);

        chat = new Chat();
        chat.setChatId(7L);
        chat.setUser1(user(1L));
        chat.setUser2(user(2L));
    }

    @Test
    @DisplayName("нове повідомлення йде обом учасникам чату")
    void newMessageGoesToBothParticipants() {
        chatNotifier.notifyNewMessage(chat, message("привіт"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(handler).sendToUser(eqLong(1L), payload.capture());
        verify(handler).sendToUser(eqLong(2L), anyString());

        assertThat(payload.getValue())
                .as("фронт розбирає саме {type, payload}")
                .contains("\"type\":\"NEW_MESSAGE\"")
                .contains("привіт");
    }

    @Test
    @DisplayName("редагування й видалення мають власні типи подій")
    void editAndDeleteHaveTheirOwnTypes() {
        chatNotifier.notifyMessageEdited(chat, message("виправлено"));
        chatNotifier.notifyMessageDeleted(chat, message(null));

        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(handler, org.mockito.Mockito.atLeast(4)).sendToUser(anyLong(), payloads.capture());

        assertThat(payloads.getAllValues())
                .anyMatch(p -> p.contains("MESSAGE_EDITED"))
                .anyMatch(p -> p.contains("MESSAGE_DELETED"));
    }

    @Test
    @DisplayName("квитанція прочитання несе список саме тих повідомлень, що прочитані")
    void readReceiptCarriesMessageIds() {
        chatNotifier.notifyReadReceipt(chat, 2L, List.of(10L, 11L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(handler).sendToUser(eqLong(1L), payload.capture());

        assertThat(payload.getValue())
                .contains("READ_RECEIPT")
                .contains("10")
                .contains("11");
    }

    @Test
    @DisplayName("порожня квитанція не шлеться взагалі")
    void emptyReceiptIsNotSent() {
        chatNotifier.notifyReadReceipt(chat, 2L, List.of());
        chatNotifier.notifyReadReceipt(chat, 2L, null);

        verify(handler, never()).sendToUser(anyLong(), anyString());
    }

    @Test
    @DisplayName("сповіщення йде лише адресату")
    void notificationGoesOnlyToRecipient() {
        var dto = new NotificationResponseDTO(1L, NotificationType.NEW_LIKE, 2L, "Богдан",
                null, null, false, LocalDateTime.now());

        notificationNotifier.notifyRecipient(5L, dto);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(handler).sendToUser(eqLong(5L), payload.capture());
        assertThat(payload.getValue()).contains("NOTIFICATION").contains("NEW_LIKE");
    }

    @Test
    @DisplayName("конверт події має рівно два поля")
    void eventEnvelopeIsSimple() throws Exception {
        WsEvent event = new WsEvent("NEW_MESSAGE", "payload");

        assertThat(event.getType()).isEqualTo("NEW_MESSAGE");
        assertThat(event.getPayload()).isEqualTo("payload");

        WsEvent empty = new WsEvent();
        empty.setType("X");
        empty.setPayload(42);
        assertThat(json.writeValueAsString(empty)).isEqualTo("{\"type\":\"X\",\"payload\":42}");
    }

    // ── допоміжне ────────────────────────────────────────────────────────

    private static long eqLong(long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private static MessageResponseDTO message(String content) {
        return new MessageResponseDTO(1L, 7L, 1L, "Аліна", content,
                LocalDateTime.now(), false, null, content == null);
    }

    private static User user(long id) {
        User u = new User();
        u.setUserId(id);
        return u;
    }
}
