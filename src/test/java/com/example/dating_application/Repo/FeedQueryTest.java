package com.example.dating_application.Repo;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.RequestStatus;
import com.example.dating_application.Entity.Tag;
import com.example.dating_application.Entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Стрічка (FR-9…FR-12) — запит, а не Java-логіка, тож перевіряти його можна лише
 * на справжній базі. Саме тут 08.09.2026 знайшовся баг: умова дивилась лише на
 * МОЇ вихідні запити, і людина, чию симпатію я прийняв, поверталася в стрічку.
 *
 * @DataJpaTest піднімає тільки JPA-шар (без веб-частини й WebSocket), кожен тест
 * іде у власній транзакції й відкочується — дані між тестами не перетікають.
 * replace = NONE: типово зріз підмінив би базу на вбудовану, а її тут немає,
 * та й перевіряти треба саме Postgres, на якому застосунок працює.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FeedQueryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private TestData data;
    private User me;

    @BeforeEach
    void setUp() {
        data = new TestData(em);
        me = data.user("Я");
    }

    /** Виклик із порожніми фільтрами — саме так ходить FeedService, коли нічого не задано. */
    private List<User> feed() {
        return userRepository.findFeedCandidates(
                me.getUserId(), false, null, null, false, null,
                false, null, false, null, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("у стрічці видно незнайомих людей")
    void showsStrangers() {
        User stranger = data.user("Незнайомка");
        data.flush();

        assertThat(feed()).extracting(User::getUserId).contains(stranger.getUserId());
    }

    @Test
    @DisplayName("себе самого в стрічці немає")
    void hidesMyself() {
        data.flush();

        assertThat(feed()).extracting(User::getUserId).doesNotContain(me.getUserId());
    }

    @Test
    @DisplayName("адміністратор у стрічку не потрапляє")
    void hidesAdmin() {
        User admin = data.admin();
        data.flush();

        assertThat(feed()).extracting(User::getUserId).doesNotContain(admin.getUserId());
    }

    @Test
    @DisplayName("забанений адміністратором зникає зі стрічки")
    void hidesBannedUser() {
        User banned = data.banned("Забанений");
        data.flush();

        assertThat(feed()).extracting(User::getUserId).doesNotContain(banned.getUserId());
    }

    @Test
    @DisplayName("той, кого я вже вподобав або пропустив, більше не показується")
    void hidesPeopleIActedOn() {
        User liked = data.user("Вподобана");
        User skipped = data.user("Пропущена");
        data.request(me, liked, RequestStatus.PENDING);
        data.request(me, skipped, RequestStatus.SKIPPED);
        data.flush();

        assertThat(feed()).extracting(User::getUserId)
                .doesNotContain(liked.getUserId(), skipped.getUserId());
    }

    @Test
    @DisplayName("РЕГРЕСІЯ: людина, чию симпатію я ПРИЙНЯВ, у стрічку не повертається")
    void hidesPersonWhoseLikeIAccepted() {
        // Рядок Request належить ЇЙ, а не мені — стара умова його не бачила,
        // і після метчу людина знову з'являлася в стрічці.
        User matched = data.user("Метч");
        data.request(matched, me, RequestStatus.ACCEPTED);
        data.flush();

        assertThat(feed()).extracting(User::getUserId).doesNotContain(matched.getUserId());
    }

    @Test
    @DisplayName("той, чию симпатію я відхилив, у стрічку не повертається")
    void hidesPersonWhoseLikeIRejected() {
        User rejected = data.user("Відхилена");
        data.request(rejected, me, RequestStatus.REJECTED);
        data.flush();

        assertThat(feed()).extracting(User::getUserId).doesNotContain(rejected.getUserId());
    }

    @Test
    @DisplayName("той, хто лайкнув мене й чекає, показується в «Симпатіях», а не у стрічці")
    void hidesPersonWaitingForMyAnswer() {
        User admirer = data.user("Шанувальниця");
        data.request(admirer, me, RequestStatus.PENDING);
        data.flush();

        assertThat(feed()).extracting(User::getUserId).doesNotContain(admirer.getUserId());
    }

    @Test
    @DisplayName("а от той, хто пропустив МЕНЕ, зі стрічки не зникає")
    void keepsPersonWhoSkippedMe() {
        // Їхній пропуск — їхня приватна справа, і він не має обмежувати мій пошук.
        User indifferent = data.user("Пропустила мене");
        data.request(indifferent, me, RequestStatus.SKIPPED);
        data.flush();

        assertThat(feed()).extracting(User::getUserId).contains(indifferent.getUserId());
    }

    @Test
    @DisplayName("блокування ховає людину в обидва боки")
    void hidesBlockedInBothDirections() {
        User iBlocked = data.user("Я заблокував");
        User blockedMe = data.user("Мене заблокували");
        data.block(me, iBlocked);
        data.block(blockedMe, me);
        data.flush();

        assertThat(feed()).extracting(User::getUserId)
                .doesNotContain(iBlocked.getUserId(), blockedMe.getUserId());
    }

    @Test
    @DisplayName("фільтри статі, міста й цілі звужують стрічку")
    void filtersNarrowTheFeed() {
        User match = data.user("Підходить", Gender.MALE, "Львів",
                DatingGoal.MARRIAGE, LocalDate.of(1990, 1, 1));
        data.user("Не підходить", Gender.FEMALE, "Київ",
                DatingGoal.FRIENDSHIP, LocalDate.of(1990, 1, 1));
        data.flush();

        List<User> filtered = userRepository.findFeedCandidates(
                me.getUserId(), true, Gender.MALE, "Львів", true, DatingGoal.MARRIAGE,
                false, null, false, null, PageRequest.of(0, 20));

        assertThat(filtered).extracting(User::getUserId).containsExactly(match.getUserId());
    }

    @Test
    @DisplayName("вік фільтрується за датою народження з обох боків")
    void filtersByAgeRange() {
        User young = data.user("Молодша", Gender.FEMALE, "Київ",
                DatingGoal.FRIENDSHIP, LocalDate.of(2004, 1, 1));
        User old = data.user("Старша", Gender.FEMALE, "Київ",
                DatingGoal.FRIENDSHIP, LocalDate.of(1970, 1, 1));
        data.flush();

        // «не молодші за 30»: народжені не пізніше ніж 30 років тому
        List<User> atLeast30 = userRepository.findFeedCandidates(
                me.getUserId(), false, null, null, false, null,
                true, LocalDate.now().minusYears(30), false, null, PageRequest.of(0, 20));

        assertThat(atLeast30).extracting(User::getUserId)
                .contains(old.getUserId())
                .doesNotContain(young.getUserId());
    }

    @Test
    @DisplayName("зверху йдуть ті, з ким більше спільних тем")
    void ranksBySharedTags() {
        Tag football = data.tag("Football");
        Tag music = data.tag("Music");
        data.give(me, football);
        data.give(me, music);

        User oneShared = data.user("Одна тема");
        data.give(oneShared, football);

        User twoShared = data.user("Дві теми");
        data.give(twoShared, football);
        data.give(twoShared, music);

        data.user("Без спільних тем");
        data.flush();

        List<Long> order = feed().stream().map(User::getUserId).toList();

        assertThat(order.indexOf(twoShared.getUserId()))
                .as("дві спільні теми мають бути вище за одну")
                .isLessThan(order.indexOf(oneShared.getUserId()));
    }
}
