package com.example.dating_application.Websocket;

import com.example.dating_application.Security.JwtHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Реєстр WebSocket-сесій і heartbeat, зокрема прибирання мертвих сесій. */
class ChatWebSocketHandlerTest {

    private static final long USER = 42L;

    private ChatWebSocketHandler handler;

    private static final long LONG_TIMEOUT = 70_000L;

    @BeforeEach
    void setUp() {
        handler = new ChatWebSocketHandler(LONG_TIMEOUT);
    }

    @Test
    @DisplayName("сесія без користувача в атрибутах одразу закривається")
    void sessionWithoutUserIsClosed() throws Exception {
        FakeSession anonymous = new FakeSession("s1", null);

        handler.afterConnectionEstablished(anonymous.asSession());

        assertThat(anonymous.closedWith).isEqualTo(CloseStatus.NOT_ACCEPTABLE);
    }

    @Test
    @DisplayName("пуш доходить до всіх відкритих сесій користувача")
    void pushReachesEveryOpenSession() throws Exception {
        FakeSession phone = connect("phone");
        FakeSession laptop = connect("laptop");

        handler.sendToUser(USER, "{\"type\":\"NEW_MESSAGE\"}");

        assertThat(phone.sentText()).containsExactly("{\"type\":\"NEW_MESSAGE\"}");
        assertThat(laptop.sentText()).containsExactly("{\"type\":\"NEW_MESSAGE\"}");
    }

    @Test
    @DisplayName("чужому користувачеві пуш не йде")
    void pushDoesNotLeakToOtherUsers() throws Exception {
        FakeSession mine = connect("mine");

        handler.sendToUser(999L, "{\"type\":\"NEW_MESSAGE\"}");

        assertThat(mine.sentText()).isEmpty();
    }

    @Test
    @DisplayName("heartbeat шле ping у кожну живу сесію")
    void heartbeatPingsLiveSessions() throws Exception {
        FakeSession session = connect("live");

        handler.heartbeat();

        assertThat(session.pings).isEqualTo(1);
        assertThat(session.closedWith).as("жива сесія не закривається").isNull();
    }

    @Test
    @DisplayName("сесія, яка мовчить довше за поріг, вважається мертвою")
    void staleSessionIsClosedAndForgotten() throws Exception {
        // Нульовий поріг, щоб не чекати 70 секунд.
        handler = new ChatWebSocketHandler(0);
        FakeSession zombie = connect("zombie");
        Thread.sleep(5);

        handler.heartbeat();

        assertThat(zombie.closedWith)
                .as("мертвий сокет прибирається сам, без afterConnectionClosed")
                .isEqualTo(CloseStatus.SESSION_NOT_RELIABLE);

        zombie.sent.clear();
        handler.sendToUser(USER, "{\"type\":\"NEW_MESSAGE\"}");
        assertThat(zombie.sentText()).as("сесії більше немає в реєстрі").isEmpty();
    }

    @Test
    @DisplayName("pong від клієнта тримає сесію живою скільки завгодно тактів")
    void pongKeepsSessionAlive() throws Exception {
        FakeSession session = connect("alive");

        for (int tick = 0; tick < 5; tick++) {
            handler.heartbeat();
            handler.handlePongMessage(session.asSession(), new PongMessage());
        }

        assertThat(session.closedWith).isNull();
        assertThat(session.pings).isEqualTo(5);
    }

    @Test
    @DisplayName("на прикладний ping клієнта приходить PONG")
    void answersApplicationPing() throws Exception {
        FakeSession session = connect("browser");

        handler.handleTextMessage(session.asSession(), new TextMessage("{\"type\":\"PING\"}"));
        handler.handleTextMessage(session.asSession(), new TextMessage("ping"));

        assertThat(session.sentText())
                .as("браузер не вміє слати ping-фрейми, тож питає текстом")
                .containsExactly("{\"type\":\"PONG\"}", "{\"type\":\"PONG\"}");
    }

    @Test
    @DisplayName("повідомлення чату через сокет не приймаються")
    void chatMessagesOverSocketAreIgnored() throws Exception {
        FakeSession session = connect("browser");

        handler.handleTextMessage(session.asSession(), new TextMessage("{\"chatId\":1,\"content\":\"привіт\"}"));

        assertThat(session.sentText())
                .as("запис іде лише через REST — інакше порядок «спершу БД» порушується")
                .isEmpty();
    }

    @Test
    @DisplayName("бан рве всі сесії користувача одразу")
    void banDisconnectsEverySession() throws Exception {
        FakeSession phone = connect("phone");
        FakeSession laptop = connect("laptop");

        handler.disconnectUser(USER);

        assertThat(phone.closedWith).isEqualTo(CloseStatus.POLICY_VIOLATION);
        assertThat(laptop.closedWith).isEqualTo(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("вихід гасить лише ту сесію, з токеном якої прийшли")
    void logoutClosesOnlyItsOwnSession() throws Exception {
        FakeSession phone = connect("phone", "jti-phone");
        FakeSession laptop = connect("laptop", "jti-laptop");

        handler.disconnectToken(USER, "jti-phone");

        assertThat(phone.closedWith)
                .as("штатний вихід, а не санкція")
                .isEqualTo(CloseStatus.NORMAL);
        assertThat(laptop.closedWith).as("другий пристрій лишається в мережі").isNull();
    }

    @Test
    @DisplayName("сесія, у яку не вдалося написати, прибирається з реєстру")
    void sessionThatFailsToWriteIsDropped() throws Exception {
        FakeSession broken = connect("broken");
        broken.failOnSend = true;

        handler.sendToUser(USER, "{\"type\":\"NEW_MESSAGE\"}");

        assertThat(broken.closedWith)
                .as("писати в мертвий сокет далі немає сенсу")
                .isEqualTo(CloseStatus.SERVER_ERROR);
    }

    @Test
    @DisplayName("збій ping'а теж прибирає сесію")
    void sessionThatFailsToPingIsDropped() throws Exception {
        FakeSession broken = connect("broken");
        broken.failOnSend = true;

        handler.heartbeat();

        assertThat(broken.closedWith).isNotNull();
        broken.sent.clear();
        handler.sendToUser(USER, "{}");
        assertThat(broken.sentText()).as("сесії більше немає в реєстрі").isEmpty();
    }

    @Test
    @DisplayName("закрита сесія пропускається без спроби запису")
    void closedSessionIsSkipped() throws Exception {
        FakeSession closed = connect("closed");
        closed.open.set(false);

        handler.sendToUser(USER, "{}");

        assertThat(closed.sentText()).isEmpty();
    }

    @Test
    @DisplayName("помилка при закритті не заважає прибрати решту сесій")
    void closeFailureDoesNotStopCleanup() throws Exception {
        FakeSession stubborn = connect("stubborn");
        stubborn.failOnClose = true;
        FakeSession normal = connect("normal");

        handler.disconnectUser(USER);

        assertThat(normal.closedWith)
                .as("перша проблемна сесія не має зривати обхід решти")
                .isEqualTo(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("пуш і розʼєднання невідомого користувача — просто нічого")
    void unknownUserIsNoOp() {
        handler.sendToUser(12345L, "{}");
        handler.disconnectUser(12345L);
        handler.disconnectToken(12345L, "jti");
        handler.disconnectToken(USER, null);
        handler.heartbeat();
    }

    @Test
    @DisplayName("сесія без користувача в реєстр не потрапляє")
    void anonymousSessionIsNotRegistered() throws Exception {
        FakeSession anonymous = new FakeSession("anon", null);

        // Головне — така сесія не має потрапити в реєстр.
        handler.handleTextMessage(anonymous.asSession(), new TextMessage("{\"type\":\"PING\"}"));
        handler.handlePongMessage(anonymous.asSession(), new PongMessage());

        anonymous.sent.clear();
        handler.sendToUser(USER, "{\"type\":\"NEW_MESSAGE\"}");
        assertThat(anonymous.sentText())
                .as("чужих повідомлень незареєстрована сесія не бачить")
                .isEmpty();
    }

    @Test
    @DisplayName("закриття з'єднання прибирає сесію з реєстру")
    void closedConnectionIsForgotten() throws Exception {
        FakeSession session = connect("bye");

        handler.afterConnectionClosed(session.asSession(), CloseStatus.NORMAL);

        session.sent.clear();
        handler.sendToUser(USER, "{}");
        assertThat(session.sentText()).isEmpty();
    }

    private FakeSession connect(String id) throws Exception {
        return connect(id, "jti-" + id);
    }

    private FakeSession connect(String id, String jti) throws Exception {
        FakeSession session = new FakeSession(id, USER, jti);
        handler.afterConnectionEstablished(session.asSession());
        return session;
    }

    private static final class FakeSession {
        private final String id;
        private final Map<String, Object> attributes = new HashMap<>();
        private final List<WebSocketMessage<?>> sent = new ArrayList<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private int pings;
        private CloseStatus closedWith;
        private boolean failOnSend;
        private boolean failOnClose;
        private WebSocketSession delegate;

        FakeSession(String id, Long userId) {
            this(id, userId, "jti-" + id);
        }

        FakeSession(String id, Long userId, String jti) {
            this.id = id;
            if (userId != null) {
                attributes.put(JwtHandshakeInterceptor.USER_ID_ATTR, userId);
                attributes.put(JwtHandshakeInterceptor.JTI_ATTR, jti);
            }
        }

        WebSocketSession asSession() throws Exception {
            if (delegate != null) {
                return delegate;
            }
            delegate = mock(WebSocketSession.class);
            when(delegate.getId()).thenReturn(id);
            when(delegate.getAttributes()).thenReturn(attributes);
            when(delegate.isOpen()).thenAnswer(inv -> open.get());

            doAnswer(inv -> {
                if (failOnSend) {
                    throw new java.io.IOException("сокет обірвано");
                }
                WebSocketMessage<?> message = inv.getArgument(0);
                if (message instanceof PingMessage) {
                    pings++;
                } else {
                    sent.add(message);
                }
                return null;
            }).when(delegate).sendMessage(any());

            doAnswer(inv -> {
                closedWith = inv.getArgument(0);
                open.set(false);
                if (failOnClose) {
                    throw new java.io.IOException("не вдалося закрити");
                }
                return null;
            }).when(delegate).close(any(CloseStatus.class));

            return delegate;
        }

        List<String> sentText() {
            return sent.stream()
                    .filter(TextMessage.class::isInstance)
                    .map(m -> ((TextMessage) m).getPayload())
                    .toList();
        }
    }
}
