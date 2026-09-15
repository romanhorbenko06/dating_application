package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Список чатів: видимість і посторінковість.
 *
 * Ключове, заради чого цей тест існує: фільтр «прибрати заблокованих» живе В САМОМУ
 * запиті. Якби він лишився в Java (як було до 10.09.2026), сторінку різали б ДО
 * відсіювання — і вона приходила б неповною, а частина чатів не потрапляла б
 * на жодну сторінку взагалі.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ChatQueryTest {

    @Autowired private ChatRepository chatRepository;
    @Autowired private EntityManager em;

    private TestData data;
    private User me;

    @BeforeEach
    void setUp() {
        data = new TestData(em);
        me = data.user("Я");
    }

    private List<Chat> visible(int page, int size) {
        return chatRepository.findVisibleChats(me.getUserId(), PageRequest.of(page, size));
    }

    @Test
    @DisplayName("видно чати, де я і перший, і другий учасник")
    void findsChatsRegardlessOfSlot() {
        User a = data.user("Перша");
        User b = data.user("Друга");
        Chat asFirst = data.chat(me, a);
        Chat asSecond = data.chat(b, me);
        data.flush();

        assertThat(visible(0, 20)).extracting(Chat::getChatId)
                .containsExactlyInAnyOrder(asFirst.getChatId(), asSecond.getChatId());
    }

    @Test
    @DisplayName("чат із забаненим співрозмовником зникає зі списку")
    void hidesChatWithBannedCompanion() {
        User banned = data.banned("Забанена");
        data.chat(me, banned);
        data.flush();

        assertThat(visible(0, 20)).isEmpty();
    }

    @Test
    @DisplayName("блокування в будь-якому напрямку ховає чат")
    void hidesChatWhenBlockedEitherWay() {
        User iBlocked = data.user("Я заблокував");
        User blockedMe = data.user("Мене заблокували");
        data.chat(me, iBlocked);
        data.chat(blockedMe, me);
        data.block(me, iBlocked);
        data.block(blockedMe, me);
        data.flush();

        assertThat(visible(0, 20)).isEmpty();
    }

    @Test
    @DisplayName("зверху чат із найсвіжішим повідомленням")
    void ordersByLastMessage() {
        User a = data.user("Стара розмова");
        User b = data.user("Свіжа розмова");
        Chat older = data.chat(me, a);
        Chat newer = data.chat(me, b);

        LocalDateTime now = LocalDateTime.now();
        data.message(older, a, "давнє", now.minusHours(3));
        data.message(newer, b, "щойно", now.minusMinutes(1));
        data.flush();

        assertThat(visible(0, 20)).extracting(Chat::getChatId)
                .containsExactly(newer.getChatId(), older.getChatId());
    }

    @Test
    @DisplayName("чати без повідомлень ідуть у кінець")
    void putsEmptyChatsLast() {
        User silent = data.user("Метч без розмови");
        User talking = data.user("З розмовою");
        Chat empty = data.chat(me, silent);
        Chat withMessages = data.chat(me, talking);
        data.message(withMessages, talking, "привіт", LocalDateTime.now().minusDays(2));
        data.flush();

        assertThat(visible(0, 20)).extracting(Chat::getChatId)
                .containsExactly(withMessages.getChatId(), empty.getChatId());
    }

    @Test
    @DisplayName("сторінки не перетинаються й разом дають повний список")
    void pagesDoNotOverlap() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            User companion = data.user("Співрозмовник " + i);
            Chat chat = data.chat(me, companion);
            data.message(chat, companion, "повідомлення " + i, now.minusMinutes(10L - i));
        }
        data.flush();

        List<Long> page0 = visible(0, 2).stream().map(Chat::getChatId).toList();
        List<Long> page1 = visible(1, 2).stream().map(Chat::getChatId).toList();
        List<Long> page2 = visible(2, 2).stream().map(Chat::getChatId).toList();
        List<Long> all = visible(0, 20).stream().map(Chat::getChatId).toList();

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);

        List<Long> joined = java.util.stream.Stream.of(page0, page1, page2)
                .flatMap(List::stream).toList();
        assertThat(joined)
                .as("склеєні сторінки = повний список у тому ж порядку")
                .containsExactlyElementsOf(all);
        assertThat(joined).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("фільтр працює ДО нарізки: перша сторінка лишається повною")
    void filterAppliesBeforeSlicing() {
        // Три чати, один із заблокованою. Сторінка на два має віддати ДВА живі чати,
        // а не «два мінус відсіяний».
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 3; i++) {
            User companion = data.user("Живий " + i);
            Chat chat = data.chat(me, companion);
            data.message(chat, companion, "текст", now.minusMinutes(i));
        }
        User blocked = data.user("Заблокована");
        Chat hidden = data.chat(me, blocked);
        data.message(hidden, blocked, "найсвіжіше", now.plusMinutes(5));
        data.block(me, blocked);
        data.flush();

        assertThat(visible(0, 2))
                .as("сторінка має бути повною попри відсіяний чат зверху")
                .hasSize(2);
        assertThat(visible(0, 20)).extracting(Chat::getChatId)
                .doesNotContain(hidden.getChatId());
    }

    @Test
    @DisplayName("чужі чати в мій список не потрапляють")
    void doesNotLeakForeignChats() {
        User a = data.user("Чужий один");
        User b = data.user("Чужий два");
        data.chat(a, b);
        data.flush();

        assertThat(visible(0, 20)).isEmpty();
    }
}
