package com.example.dating_application.Api;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.ComplaintRepository;
import com.example.dating_application.Repo.MessageRepository;
import com.example.dating_application.Repo.NotificationRepository;
import com.example.dating_application.Repo.PhotoRepository;
import com.example.dating_application.Repo.RequestRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Життя акаунта: редагування анкети (PUT і PATCH) та ВИДАЛЕННЯ.
 *
 * Видалення тут найважливіше: воно має прибрати за собою геть усе — симпатії,
 * чати з листуванням, скарги, сповіщення, фото. Рядок, що лишився висіти,
 * означає або биті посилання в чужих екранах, або чужі персональні дані
 * в базі після того, як людина попросила її стерти.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountLifecycleApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private ChatRepository chatRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PhotoRepository photoRepository;
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

    @Test
    @DisplayName("PATCH змінює лише названі поля")
    void patchTouchesOnlyNamedFields() throws Exception {
        mvc.perform(patch("/api/users/" + alice.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Львів\"}"))
                .andExpect(status().isOk());

        JsonNode me = getJson(aliceToken, "/api/users/me");
        assertThat(me.get("city").asText()).isEqualTo("Львів");
        assertThat(me.get("name").asText()).as("імені не чіпали").isEqualTo("Аліна");
    }

    @Test
    @DisplayName("PUT замінює анкету цілком")
    void putReplacesWholeProfile() throws Exception {
        mvc.perform(put("/api/users/" + alice.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Аліна Нова","gender":"FEMALE","dateOfBirth":"1994-04-04",
                                 "city":"Одеса","datingGoal":"MARRIAGE","characterisation":"текст",
                                 "educationLevel":null,"temperament":null,"childrenStatus":null}
                                """))
                .andExpect(status().isOk());

        JsonNode me = getJson(aliceToken, "/api/users/me");
        assertThat(me.get("name").asText()).isEqualTo("Аліна Нова");
        assertThat(me.get("city").asText()).isEqualTo("Одеса");
        assertThat(me.get("datingGoal").asText()).isEqualTo("MARRIAGE");
    }

    @Test
    @DisplayName("чужу анкету редагувати не можна")
    void cannotEditSomeoneElsesProfile() throws Exception {
        mvc.perform(patch("/api/users/" + bohdan.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Захоплено\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("невалідні дані анкети відхиляються з поясненням по полях")
    void profileValidationReportsFields() throws Exception {
        MvcResult result = mvc.perform(patch("/api/users/" + alice.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"я\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).get("fieldErrors").has("name")).isTrue();
    }

    @Test
    @DisplayName("видалення акаунта прибирає ЗА СОБОЮ все: симпатії, чат, листування, скаргу, сповіщення, фото")
    void deletingAccountCleansEverythingUp() throws Exception {
        // Створюємо повний слід: метч, переписку, скаргу й сповіщення
        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + alice.getUserId() + "}"))
                .andExpect(status().isOk());
        long requestId = getJson(aliceToken, "/api/requests/incoming").get(0).get("requestId").asLong();
        MvcResult accepted = mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        long chatId = body(accepted).get("chatId").asLong();

        mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":" + chatId + ",\"content\":\"привіт\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/complaints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedUserId\":" + alice.getUserId() + ",\"reason\":\"перевірка\"}"))
                .andExpect(status().isOk());

        long aliceId = alice.getUserId();
        assertThat(messageRepository.count()).isPositive();

        mvc.perform(delete("/api/users/" + aliceId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(aliceId)).as("сам акаунт").isEmpty();
        assertThat(chatRepository.findById(chatId)).as("чат").isEmpty();
        assertThat(messageRepository.findByChatChatIdOrderBySentAt(chatId))
                .as("листування")
                .isEmpty();
        assertThat(requestRepository.findById(requestId)).as("симпатія").isEmpty();
        assertThat(complaintRepository.findByReportedUserUserIdOrderByCreatedAtDescComplainIdDesc(
                aliceId, org.springframework.data.domain.PageRequest.of(0, 50)))
                .as("скарги на видаленого")
                .isEmpty();
        assertThat(notificationRepository.countVisibleUnread(aliceId))
                .as("сповіщення")
                .isZero();
        assertThat(photoRepository.findByUserUserId(aliceId)).as("фото").isEmpty();

        assertThat(getJson(bohdanToken, "/api/chats").size())
                .as("у співрозмовника чат теж зникає, а не лишається битим")
                .isZero();
    }

    @Test
    @DisplayName("чужий акаунт видалити не можна")
    void cannotDeleteSomeoneElsesAccount() throws Exception {
        mvc.perform(delete("/api/users/" + bohdan.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(bohdan.getUserId())).isPresent();
    }

    @Test
    @DisplayName("чужа анкета не показує пошту, своя — показує")
    void emailIsVisibleOnlyToOwner() throws Exception {
        assertThat(getJson(aliceToken, "/api/users/me").has("email")).isTrue();
        assertThat(getJson(aliceToken, "/api/users/" + bohdan.getUserId()).has("email")).isFalse();
    }

    // ── допоміжне ────────────────────────────────────────────────────────

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
