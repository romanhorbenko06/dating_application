package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Список тих, хто чекає відповіді, і накопичувальний лічильник «вподобали вас» — різні величини. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IncomingLikesQueryTest {

    @Autowired private RequestRepository requestRepository;
    @Autowired private EntityManager em;

    private TestData data;
    private User me;

    @BeforeEach
    void setUp() {
        data = new TestData(em);
        me = data.user("Я");
    }

    private List<Request> pending() {
        return requestRepository.findIncomingVisible(
                me.getUserId(), RequestStatus.PENDING, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("у списку лише ті, хто чекає відповіді")
    void listShowsOnlyPending() {
        User waiting = data.user("Чекає");
        User accepted = data.user("Прийнята");
        User rejected = data.user("Відхилена");
        data.request(waiting, me, RequestStatus.PENDING);
        data.request(accepted, me, RequestStatus.ACCEPTED);
        data.request(rejected, me, RequestStatus.REJECTED);
        data.flush();

        assertThat(pending()).hasSize(1);
        assertThat(pending().get(0).getFromUser().getUserId()).isEqualTo(waiting.getUserId());
    }

    @Test
    @DisplayName("лічильник рахує всі симпатії за весь час, а не лише нерозібрані")
    void countIncludesAnsweredLikes() {
        data.request(data.user("Чекає"), me, RequestStatus.PENDING);
        data.request(data.user("Прийнята"), me, RequestStatus.ACCEPTED);
        data.request(data.user("Відхилена"), me, RequestStatus.REJECTED);
        data.flush();

        assertThat(requestRepository.countIncomingLikes(me.getUserId()))
                .as("саме тому плитка в анкеті не обнуляється після розбору симпатій")
                .isEqualTo(3);
        assertThat(pending()).hasSize(1);
    }

    @Test
    @DisplayName("пропуск не рахується за симпатію")
    void countIgnoresSkips() {
        data.request(data.user("Пропустила мене"), me, RequestStatus.SKIPPED);
        data.flush();

        assertThat(requestRepository.countIncomingLikes(me.getUserId())).isZero();
    }

    @Test
    @DisplayName("симпатії від забанених не рахуються й не показуються")
    void ignoresBannedSenders() {
        data.request(data.banned("Забанена"), me, RequestStatus.PENDING);
        data.flush();

        assertThat(requestRepository.countIncomingLikes(me.getUserId())).isZero();
        assertThat(pending()).isEmpty();
    }

    @Test
    @DisplayName("симпатії від заблокованих не рахуються й не показуються — в обидва боки")
    void ignoresBlockedInBothDirections() {
        User iBlocked = data.user("Я заблокував");
        User blockedMe = data.user("Мене заблокували");
        data.request(iBlocked, me, RequestStatus.PENDING);
        data.request(blockedMe, me, RequestStatus.PENDING);
        data.block(me, iBlocked);
        data.block(blockedMe, me);
        data.flush();

        assertThat(requestRepository.countIncomingLikes(me.getUserId())).isZero();
        assertThat(pending()).isEmpty();
    }

    @Test
    @DisplayName("свої надіслані симпатії у вхідних не з'являються")
    void doesNotCountOutgoing() {
        data.request(me, data.user("Кому надіслав"), RequestStatus.PENDING);
        data.flush();

        assertThat(requestRepository.countIncomingLikes(me.getUserId())).isZero();
        assertThat(pending()).isEmpty();
    }
}
