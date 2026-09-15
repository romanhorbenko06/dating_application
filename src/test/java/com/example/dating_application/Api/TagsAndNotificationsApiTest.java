package com.example.dating_application.Api;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Теми (FR-8.3) і центр сповіщень (FR-18, FR-19, UC-11).
 *
 * Теми — те, за чим стрічка ранжує людей, тож «додав тег» має бути помітно
 * одразу. Сповіщення перевіряються не поодинці, а як наслідок дій: лайк
 * породжує NEW_LIKE, взаємність — NEW_MATCH обом, повідомлення — NEW_MESSAGE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TagsAndNotificationsApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private User alice;
    private User bohdan;
    private String aliceToken;
    private String bohdanToken;

    @BeforeEach
    void setUp() {
        alice = newUser("Аліна");
        bohdan = newUser("Богдан");
        aliceToken = jwtUtil.generateToken(alice);
        bohdanToken = jwtUtil.generateToken(bohdan);
    }

    // ── теми ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("довідник тем не порожній — його засіває застосунок при старті")
    void tagCatalogIsSeeded() throws Exception {
        JsonNode tags = getJson(aliceToken, "/api/tags");

        assertThat(tags.size()).isGreaterThan(50);
        assertThat(tags.get(0).has("tagId")).isTrue();
        assertThat(tags.get(0).has("tagName")).isTrue();
    }

    @Test
    @DisplayName("обрана тема з'являється в моєму списку і видима іншим")
    void chosenTagBecomesVisible() throws Exception {
        long tagId = anyTagId();

        mvc.perform(post("/api/tags/my/" + tagId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(idsOf(getJson(aliceToken, "/api/tags/my"))).contains(tagId);
        assertThat(idsOf(getJson(bohdanToken, "/api/tags/user/" + alice.getUserId())))
                .as("теми відкриті: за ними будується сумісність")
                .contains(tagId);
    }

    @Test
    @DisplayName("повторне додавання тієї самої теми не дає дубля")
    void addingSameTagTwiceIsHarmless() throws Exception {
        long tagId = anyTagId();

        mvc.perform(post("/api/tags/my/" + tagId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tags/my/" + tagId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(idsOf(getJson(aliceToken, "/api/tags/my")).stream().filter(id -> id == tagId).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("тему можна прибрати")
    void tagCanBeRemoved() throws Exception {
        long tagId = anyTagId();
        mvc.perform(post("/api/tags/my/" + tagId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/tags/my/" + tagId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(idsOf(getJson(aliceToken, "/api/tags/my"))).doesNotContain(tagId);
    }

    @Test
    @DisplayName("неіснуючу тему додати не можна")
    void unknownTagIsRejected() throws Exception {
        mvc.perform(post("/api/tags/my/999999").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("без токена теми недоступні")
    void tagsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/tags")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tags/my")).andExpect(status().isUnauthorized());
    }

    // ── сповіщення ───────────────────────────────────────────────────────

    @Test
    @DisplayName("симпатія породжує сповіщення адресату (FR-19.2)")
    void likeCreatesNotification() throws Exception {
        like(bohdanToken, alice.getUserId());

        JsonNode notifications = getJson(aliceToken, "/api/notifications");

        assertThat(notifications.size()).isEqualTo(1);
        assertThat(notifications.get(0).get("type").asText()).isEqualTo("NEW_LIKE");
        assertThat(notifications.get(0).get("actorName").asText()).isEqualTo("Богдан");
        assertThat(notifications.get(0).get("chatId").isNull())
                .as("на цьому етапі чату ще немає")
                .isTrue();
    }

    @Test
    @DisplayName("взаємність сповіщає ОБОХ і несе chatId")
    void matchNotifiesBothSides() throws Exception {
        like(bohdanToken, alice.getUserId());
        acceptFirstIncoming(aliceToken);

        for (String token : new String[] {aliceToken, bohdanToken}) {
            JsonNode match = findByType(getJson(token, "/api/notifications"), "NEW_MATCH");
            assertThat(match).as("метч має побачити кожен").isNotNull();
            assertThat(match.get("chatId").isNull())
                    .as("зі сповіщення можна одразу перейти в чат")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("лічильник непрочитаних росте й гаситься одним рухом")
    void unreadCounterGrowsAndClears() throws Exception {
        like(bohdanToken, alice.getUserId());

        assertThat(unreadCount(aliceToken)).isEqualTo(1);

        mvc.perform(put("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(unreadCount(aliceToken)).isZero();
    }

    @Test
    @DisplayName("фільтр «лише непрочитані» справді фільтрує")
    void unreadOnlyFilterWorks() throws Exception {
        like(bohdanToken, alice.getUserId());
        assertThat(getJson(aliceToken, "/api/notifications?unreadOnly=true").size()).isEqualTo(1);

        mvc.perform(put("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(getJson(aliceToken, "/api/notifications?unreadOnly=true").size()).isZero();
        assertThat(getJson(aliceToken, "/api/notifications").size())
                .as("прочитані лишаються в історії")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("сповіщення про повідомлення ЗГОРТАЮТЬСЯ в один запис із лічильником")
    void messageNotificationsAreCollapsed() throws Exception {
        like(bohdanToken, alice.getUserId());
        long chatId = acceptFirstIncoming(aliceToken);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/chats/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"chatId\":" + chatId + ",\"content\":\"повідомлення " + i + "\"}"))
                    .andExpect(status().isOk());
        }

        JsonNode messageNotifications = getJson(bohdanToken, "/api/notifications");
        JsonNode collapsed = findByType(messageNotifications, "NEW_MESSAGE");

        assertThat(collapsed).isNotNull();
        assertThat(collapsed.get("messageCount").asInt())
                .as("три повідомлення — один запис, інакше центр сповіщень заливало б")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("чужих сповіщень не видно")
    void notificationsArePrivate() throws Exception {
        like(bohdanToken, alice.getUserId());

        assertThat(getJson(bohdanToken, "/api/notifications").size())
                .as("той, хто лайкнув, сповіщення про власний лайк не отримує")
                .isZero();
    }

    @Test
    @DisplayName("межі сторінки сповіщень перевіряються")
    void notificationPagingIsValidated() throws Exception {
        mvc.perform(get("/api/notifications?page=-1").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications?size=0").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    // ── допоміжне ────────────────────────────────────────────────────────

    private void like(String token, long toUserId) throws Exception {
        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + toUserId + "}"))
                .andExpect(status().isOk());
    }

    private long acceptFirstIncoming(String token) throws Exception {
        long requestId = getJson(token, "/api/requests/incoming").get(0).get("requestId").asLong();
        MvcResult accepted = mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return body(accepted).get("chatId").asLong();
    }

    private int unreadCount(String token) throws Exception {
        return getJson(token, "/api/notifications/unread-count").get("unreadCount").asInt();
    }

    private long anyTagId() throws Exception {
        return getJson(aliceToken, "/api/tags").get(0).get("tagId").asLong();
    }

    private static java.util.List<Long> idsOf(JsonNode tags) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        tags.forEach(tag -> ids.add(tag.get("tagId").asLong()));
        return ids;
    }

    private static JsonNode findByType(JsonNode notifications, String type) {
        for (JsonNode n : notifications) {
            if (type.equals(n.get("type").asText())) {
                return n;
            }
        }
        return null;
    }

    private JsonNode getJson(String token, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private User newUser(String name) {
        User u = new User();
        u.setName(name);
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordhash("{noop}irrelevant");
        u.setGender(Gender.FEMALE);
        u.setCity("Київ");
        u.setDatingGoal(DatingGoal.FRIENDSHIP);
        u.setDateOfBirth(LocalDate.of(1995, 5, 5));
        u.setRole(Role.DATER);
        u.setBlocked(false);
        return userRepository.save(u);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
