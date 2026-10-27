package com.example.dating_application.Api;

import com.example.dating_application.Repo.VerificationCodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Наскрізний шлях користувача від реєстрації до переписки; код береться з БД. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserJourneyE2ETest {

    private static final String PASSWORD = "Passw0rd!23";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private VerificationCodeRepository verificationCodes;

    @Test
    @DisplayName("шлях цілком: реєстрація → код → вхід → симпатія → метч → повідомлення")
    void fullJourneyFromRegistrationToConversation() throws Exception {
        String aliceEmail = register("Аліна");
        String bohdanEmail = register("Богдан");

        String aliceToken = verifyAndLogin(aliceEmail);
        String bohdanToken = verifyAndLogin(bohdanEmail);

        long aliceId = myId(aliceToken);
        long bohdanId = myId(bohdanToken);

        send(bohdanToken, aliceId);
        assertThat(chats(aliceToken).size()).as("до взаємності чат не створюється").isZero();

        long requestId = incoming(aliceToken).get(0).get("requestId").asLong();
        MvcResult accepted = mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        long chatId = body(accepted).get("chatId").asLong();

        assertThat(chats(aliceToken).size()).isEqualTo(1);
        assertThat(chats(bohdanToken).size()).as("чат бачать обидва").isEqualTo(1);

        mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":" + chatId + ",\"content\":\"Привіт!\"}"))
                .andExpect(status().isOk());

        JsonNode messages = getJson(bohdanToken, "/api/chats/" + chatId + "/messages");
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0).get("content").asText()).isEqualTo("Привіт!");
        assertThat(messages.get(0).get("senderId").asLong()).isEqualTo(aliceId);

        JsonNode count = getJson(aliceToken, "/api/requests/incoming/count");
        assertThat(count.get("count").asInt()).isEqualTo(1);

        assertThat(bohdanId).isNotEqualTo(aliceId);
    }

    @Test
    @DisplayName("невірний код не створює акаунт")
    void wrongCodeDoesNotCreateAccount() throws Exception {
        String email = register("Помилковий код");

        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, "000000")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(notOk());
    }

    @Test
    @DisplayName("вихід гасить саме той токен, з яким прийшли")
    void logoutRevokesTheToken() throws Exception {
        String token = verifyAndLogin(register("Той, хто вийде"));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("перебір пароля впирається в ліміт і отримує Retry-After")
    void bruteForceHitsRateLimit() throws Exception {
        String email = verifyAndRegisterOnly("Жертва перебору");

        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"невірний\"}"))
                    .andExpect(notOk());
        }

        MvcResult limited = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"невірний\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(body(limited).get("retryAfterSeconds").asInt())
                .as("клієнт має дізнатись, скільки чекати")
                .isPositive();
        assertThat(limited.getResponse().getHeader("Retry-After")).isNotNull();

        // Навіть із правильним паролем ліміт тримає.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("блокування прибирає чат в обох учасників")
    void blockRemovesChatForBoth() throws Exception {
        String aliceToken = verifyAndLogin(register("Аліна Б"));
        String bohdanToken = verifyAndLogin(register("Богдан Б"));
        long aliceId = myId(aliceToken);
        long bohdanId = myId(bohdanToken);

        send(bohdanToken, aliceId);
        long requestId = incoming(aliceToken).get(0).get("requestId").asLong();
        mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
        assertThat(chats(aliceToken).size()).isEqualTo(1);

        mvc.perform(post("/api/blocks/" + bohdanId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(chats(aliceToken).size()).as("у того, хто заблокував").isZero();
        assertThat(chats(bohdanToken).size()).as("і в того, кого заблокували").isZero();
    }

    private String register(String name) throws Exception {
        String email = UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"%s","gender":"FEMALE",
                                 "dateOfBirth":"1995-05-05","city":"Київ","datingGoal":"FRIENDSHIP"}
                                """.formatted(email, name)))
                .andExpect(status().isOk());
        return email;
    }

    private String verifyAndLogin(String email) throws Exception {
        String code = verificationCodes.findByEmail(email).orElseThrow().getCode();

        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code)))
                .andExpect(status().isOk());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return body(login).get("token").asText().replace("Bearer ", "");
    }

    private String verifyAndRegisterOnly(String name) throws Exception {
        String email = register(name);
        String code = verificationCodes.findByEmail(email).orElseThrow().getCode();
        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code)))
                .andExpect(status().isOk());
        return email;
    }

    private void send(String token, long toUserId) throws Exception {
        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + toUserId + "}"))
                .andExpect(status().isOk());
    }

    private static String verifyBody(String email, String code) {
        return """
                {"email":"%s","password":"%s","verificationCode":"%s"}
                """.formatted(email, PASSWORD, code);
    }

    private long myId(String token) throws Exception {
        return getJson(token, "/api/users/me").get("userId").asLong();
    }

    private JsonNode chats(String token) throws Exception {
        return getJson(token, "/api/chats");
    }

    private JsonNode incoming(String token) throws Exception {
        return getJson(token, "/api/requests/incoming");
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

    private static ResultMatcher notOk() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("вхід не мав пройти")
                .isGreaterThanOrEqualTo(400);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
