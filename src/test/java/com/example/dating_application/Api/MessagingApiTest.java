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
 * Листування (UC-10, FR-17): надсилання, історія з курсором, редагування,
 * м'яке видалення, квитанції прочитання.
 *
 * Через API, бо правила розкидані між контролером (@PreAuthorize), сервісом
 * (подвійна перевірка учасника) і DTO — і саме стик між ними тут найцікавіший.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessagingApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private User alice;
    private User bohdan;
    private String aliceToken;
    private String bohdanToken;
    private long chatId;

    @BeforeEach
    void setUp() throws Exception {
        alice = newUser("Аліна");
        bohdan = newUser("Богдан");
        aliceToken = jwtUtil.generateToken(alice);
        bohdanToken = jwtUtil.generateToken(bohdan);
        chatId = createMatch();
    }

    @Test
    @DisplayName("повідомлення доходить і має правильного автора")
    void messageArrivesWithItsAuthor() throws Exception {
        long id = send(aliceToken, "Привіт!");

        JsonNode history = messages(bohdanToken, "");
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("messageId").asLong()).isEqualTo(id);
        assertThat(history.get(0).get("senderId").asLong()).isEqualTo(alice.getUserId());
        assertThat(history.get(0).get("isRead").asBoolean())
                .as("щойно надіслане ще не прочитане")
                .isFalse();
    }

    @Test
    @DisplayName("порожнє повідомлення не приймається")
    void emptyMessageIsRejected() throws Exception {
        mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":" + chatId + ",\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("у чужий чат написати не можна")
    void cannotWriteIntoForeignChat() throws Exception {
        User outsider = newUser("Стороння");

        mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken(outsider)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":" + chatId + ",\"content\":\"підгляданка\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("історія гортається курсором: before віддає старіші")
    void historyPagesByCursor() throws Exception {
        long first = send(aliceToken, "перше");
        send(aliceToken, "друге");
        long third = send(aliceToken, "третє");

        JsonNode lastTwo = messages(aliceToken, "?limit=2");
        assertThat(lastTwo.size()).isEqualTo(2);
        assertThat(lastTwo.get(1).get("messageId").asLong())
                .as("повертається в хронологічному порядку, найсвіжіше в кінці")
                .isEqualTo(third);

        long oldestShown = lastTwo.get(0).get("messageId").asLong();
        JsonNode older = messages(aliceToken, "?before=" + oldestShown);
        assertThat(older.size()).isEqualTo(1);
        assertThat(older.get(0).get("messageId").asLong()).isEqualTo(first);
    }

    @Test
    @DisplayName("автор може виправити своє повідомлення — і це видно")
    void authorCanEditOwnMessage() throws Exception {
        long id = send(aliceToken, "првіт");

        MvcResult edited = mvc.perform(put("/api/chats/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"привіт\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(edited).get("content").asText()).isEqualTo("привіт");
        assertThat(body(edited).get("editedAt").isNull())
                .as("позначка про редагування має з'явитись — співрозмовник бачить, що текст змінили")
                .isFalse();
    }

    @Test
    @DisplayName("чуже повідомлення редагувати не можна")
    void cannotEditSomeoneElsesMessage() throws Exception {
        long id = send(aliceToken, "моє");

        mvc.perform(put("/api/chats/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"переписано\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("видалення М'ЯКЕ: рядок лишається, вміст стирається")
    void deleteKeepsTheRowButWipesContent() throws Exception {
        long id = send(aliceToken, "помилкове");

        MvcResult deleted = mvc.perform(delete("/api/chats/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(deleted).get("isDeleted").asBoolean()).isTrue();
        assertThat(body(deleted).get("content").isNull())
                .as("вміст прибирається реально, а не ховається у відповіді")
                .isTrue();

        JsonNode history = messages(bohdanToken, "");
        assertThat(history.size()).as("рядок лишається у стрічці").isEqualTo(1);
        assertThat(history.get(0).get("isDeleted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("видалене не можна відредагувати")
    void deletedMessageCannotBeEdited() throws Exception {
        long id = send(aliceToken, "зникне");
        mvc.perform(delete("/api/chats/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/chats/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"повернулось\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("чуже повідомлення видалити не можна")
    void cannotDeleteSomeoneElsesMessage() throws Exception {
        long id = send(aliceToken, "моє");

        mvc.perform(delete("/api/chats/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("позначення прочитаним стосується лише чужих повідомлень і працює один раз")
    void markReadIsIdempotentAndOnlyForIncoming() throws Exception {
        send(aliceToken, "перше");
        send(aliceToken, "друге");

        MvcResult first = mvc.perform(put("/api/chats/" + chatId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(first).get("markedCount").asInt()).isEqualTo(2);

        MvcResult again = mvc.perform(put("/api/chats/" + chatId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(again).get("markedCount").asInt())
                .as("повторний виклик нічого не позначає й не шле квитанцію")
                .isZero();

        assertThat(messages(aliceToken, "").get(0).get("isRead").asBoolean())
                .as("автор бачить, що його прочитали")
                .isTrue();
    }

    @Test
    @DisplayName("своїх власних повідомлень прочитаними не позначають")
    void ownMessagesAreNotMarkedRead() throws Exception {
        send(aliceToken, "моє");

        MvcResult result = mvc.perform(put("/api/chats/" + chatId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(result).get("markedCount").asInt()).isZero();
    }

    @Test
    @DisplayName("деталі чужого чату недоступні")
    void chatDetailsAreProtected() throws Exception {
        User outsider = newUser("Стороння");

        mvc.perform(get("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken(outsider))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/chats/" + chatId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("деталі власного чату містять обох учасників і кількість спільних тем")
    void chatDetailsCarryBothParticipants() throws Exception {
        MvcResult result = mvc.perform(get("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode chat = body(result);
        assertThat(chat.get("chatId").asLong()).isEqualTo(chatId);
        assertThat(chat.has("user1Name")).isTrue();
        assertThat(chat.has("user2Name")).isTrue();
        assertThat(chat.has("sharedTagCount")).isTrue();
    }

    @Test
    @DisplayName("повідомлення в неіснуючий чат — зрозуміла помилка")
    void unknownChatIsReported() throws Exception {
        mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":999999,\"content\":\"нікуди\"}"))
                .andExpect(status().isForbidden());
    }

    // ── допоміжне ────────────────────────────────────────────────────────

    private long createMatch() throws Exception {
        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + alice.getUserId() + "}"))
                .andExpect(status().isOk());

        MvcResult incoming = mvc.perform(get("/api/requests/incoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        long requestId = body(incoming).get(0).get("requestId").asLong();

        MvcResult accepted = mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        return body(accepted).get("chatId").asLong();
    }

    private long send(String token, String text) throws Exception {
        MvcResult result = mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":" + chatId + ",\"content\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("messageId").asLong();
    }

    private JsonNode messages(String token, String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/chats/" + chatId + "/messages" + query)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
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
