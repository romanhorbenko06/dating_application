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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Симпатії (UC-08, UC-09) і блокування (UC-07) через API: списки, відмова,
 * пропуск, симетрія блокування.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestsAndBlocksApiTest {

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

    // ── симпатії ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("надіслана симпатія бачиться в «надісланих», а вхідна — в «отриманих»")
    void listsShowBothDirections() throws Exception {
        like(bohdanToken, alice.getUserId());

        JsonNode sent = getJson(bohdanToken, "/api/requests/sent");
        assertThat(sent.size()).isEqualTo(1);
        assertThat(sent.get(0).get("toUserName").asText()).isEqualTo("Аліна");
        assertThat(sent.get(0).get("status").asText()).isEqualTo("PENDING");

        JsonNode incoming = getJson(aliceToken, "/api/requests/incoming");
        assertThat(incoming.size()).isEqualTo(1);
        assertThat(incoming.get(0).get("fromUserName").asText()).isEqualTo("Богдан");
    }

    @Test
    @DisplayName("відхилена симпатія зникає з вхідних, але лишається в надісланих зі статусом")
    void rejectedRequestKeepsItsTrace() throws Exception {
        like(bohdanToken, alice.getUserId());
        long requestId = getJson(aliceToken, "/api/requests/incoming").get(0).get("requestId").asLong();

        mvc.perform(post("/api/requests/" + requestId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(getJson(aliceToken, "/api/requests/incoming").size()).isZero();
        assertThat(getJson(bohdanToken, "/api/requests/sent").get(0).get("status").asText())
                .as("той, хто надіслав, має бачити результат")
                .isEqualTo("REJECTED");
        assertThat(getJson(aliceToken, "/api/chats").size()).as("чату не створюється").isZero();
    }

    @Test
    @DisplayName("двічі відповісти на ту саму симпатію не можна")
    void cannotAnswerTwice() throws Exception {
        like(bohdanToken, alice.getUserId());
        long requestId = getJson(aliceToken, "/api/requests/incoming").get(0).get("requestId").asLong();

        mvc.perform(post("/api/requests/" + requestId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("на чужу симпатію відповісти не можна")
    void cannotAnswerSomeoneElsesRequest() throws Exception {
        like(bohdanToken, alice.getUserId());
        long requestId = getJson(aliceToken, "/api/requests/incoming").get(0).get("requestId").asLong();
        User outsider = newUser("Стороння");

        mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("пропущені збираються в окремий список")
    void skippedProfilesHaveTheirOwnList() throws Exception {
        mvc.perform(post("/api/requests/skip/" + bohdan.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        JsonNode skipped = getJson(aliceToken, "/api/requests/skipped");

        assertThat(skipped.size()).isEqualTo(1);
        assertThat(skipped.get(0).get("userId").asLong()).isEqualTo(bohdan.getUserId());
        assertThat(getJson(bohdanToken, "/api/requests/incoming").size())
                .as("пропуск НЕ сповіщає того, кого пропустили")
                .isZero();
    }

    @Test
    @DisplayName("межі сторінок у списках симпатій перевіряються")
    void requestPagingIsValidated() throws Exception {
        mvc.perform(get("/api/requests/incoming?page=-1").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests/sent?size=101").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    // ── блокування ───────────────────────────────────────────────────────

    @Test
    @DisplayName("блокування симетричне: анкети зникають в обох")
    void blockIsSymmetric() throws Exception {
        block(aliceToken, bohdan.getUserId());

        mvc.perform(get("/api/users/" + bohdan.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + alice.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("заблокованого видно у власному списку блокувань і можна розблокувати")
    void blockListIsManageable() throws Exception {
        block(aliceToken, bohdan.getUserId());

        JsonNode blocks = getJson(aliceToken, "/api/blocks");
        assertThat(blocks.size()).isEqualTo(1);
        assertThat(blocks.get(0).get("blockedUserName").asText()).isEqualTo("Богдан");

        mvc.perform(delete("/api/blocks/" + bohdan.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(getJson(aliceToken, "/api/blocks").size()).isZero();
        mvc.perform(get("/api/users/" + bohdan.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("повторне блокування ідемпотентне, а зняття неіснуючого — помилка")
    void blockOperationsAreGuarded() throws Exception {
        block(aliceToken, bohdan.getUserId());

        // Повторний виклик свідомо НЕ помилка: клієнт міг двічі натиснути,
        // і другий рядок у базі з цього з'являтись не повинен
        block(aliceToken, bohdan.getUserId());
        assertThat(getJson(aliceToken, "/api/blocks").size()).isEqualTo(1);

        // А от зняти те, чого немає, — уже неоднозначність, і про неї кажемо прямо
        mvc.perform(delete("/api/blocks/" + newUser("Хтось").getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("адміністратора заблокувати не можна")
    void cannotBlockAdmin() throws Exception {
        User admin = newUser("Модератор");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        mvc.perform(post("/api/blocks/" + admin.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("самого себе заблокувати не можна")
    void cannotBlockYourself() throws Exception {
        mvc.perform(post("/api/blocks/" + alice.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("після блокування симпатію не надіслати")
    void blockedUsersCannotLikeEachOther() throws Exception {
        block(aliceToken, bohdan.getUserId());

        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + alice.getUserId() + "}"))
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

    private void block(String token, long userId) throws Exception {
        mvc.perform(post("/api/blocks/" + userId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private JsonNode getJson(String token, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
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
