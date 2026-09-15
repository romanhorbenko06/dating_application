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
 * Модерація цілком (UC-13, UC-14): від скарги користувача до бану порушника.
 *
 * Саме той шлях, яким на захисті йтимуть найпершим, і водночас найменш покритий
 * до цього тесту. Перевіряється не лише «ендпоінт відповів 200», а наслідки:
 * забанений втрачає доступ, скарга змінює статус, дейтер до адмінки не доходить.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private User reporter;
    private User offender;
    private String reporterToken;
    private String offenderToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        reporter = newUser("Скаржниця", Role.DATER);
        offender = newUser("Порушник", Role.DATER);
        User admin = newUser("Модератор", Role.ADMIN);

        reporterToken = jwtUtil.generateToken(reporter);
        offenderToken = jwtUtil.generateToken(offender);
        adminToken = jwtUtil.generateToken(admin);
    }

    @Test
    @DisplayName("повний цикл: скарга → черга модерації → розгляд → вирішено → бан")
    void fullModerationCycle() throws Exception {
        long complaintId = fileComplaint("ображає в переписці");

        // Скарга стоїть у черзі саме як нерозглянута
        JsonNode pending = adminGet("/api/admin/complaints?status=PENDING");
        assertThat(findById(pending, complaintId)).isNotNull();
        assertThat(findById(pending, complaintId).get("reportedUserName").asText())
                .isEqualTo("Порушник");

        // Модератор бере в роботу
        mvc.perform(put("/api/admin/complaints/" + complaintId + "/review")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(findById(adminGet("/api/admin/complaints?status=PENDING"), complaintId))
                .as("із черги нерозглянутих скарга має піти")
                .isNull();

        // Порушення підтвердилось
        MvcResult resolved = mvc.perform(put("/api/admin/complaints/" + complaintId + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(resolved).get("status").asText()).isEqualTo("RESOLVED");

        // І бан порушника
        mvc.perform(put("/api/admin/users/" + offender.getUserId() + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"підтверджені образи\"}"))
                .andExpect(status().isOk());

        assertThat(findById(adminGet("/api/admin/users/blocked"), "userId", offender.getUserId()))
                .as("забанений має з'явитись у списку")
                .isNotNull();

        // Раніше виданий токен перестає діяти одразу: JwtFilter бачить бан і не
        // автентифікує запит, тож приходить 401, а не 403. Причину бану людина
        // побачить при спробі увійти — саме там вона щось означає.
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(offenderToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("забаненому пояснюють причину при спробі входу, а не мовчки не пускають")
    void bannedUserSeesReasonOnLogin() throws Exception {
        String email = offender.getEmail();
        mvc.perform(put("/api/admin/users/" + offender.getUserId() + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"розсилання спаму\"}"))
                .andExpect(status().isOk());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"будь-який\"}"))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode response = body(login);
        assertThat(response.get("message").asText()).isEqualTo("Account is permanently blocked");
        assertThat(response.get("reason").asText())
                .as("саме цей текст фронт показує людині")
                .isEqualTo("розсилання спаму");
    }

    @Test
    @DisplayName("безпідставну скаргу можна відхилити — санкцій не буде")
    void baselessComplaintIsRejected() throws Exception {
        long complaintId = fileComplaint("просто не сподобався");

        MvcResult rejected = mvc.perform(put("/api/admin/complaints/" + complaintId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(rejected).get("status").asText()).isEqualTo("REJECTED");
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(offenderToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("модератор бачить усі скарги на конкретну людину")
    void complaintsAboutOneUserAreGroupedTogether() throws Exception {
        fileComplaint("перша скарга");
        User another = newUser("Другий скаржник", Role.DATER);
        fileComplaintFrom(jwtUtil.generateToken(another), "друга скарга");

        JsonNode about = adminGet("/api/admin/complaints/user/" + offender.getUserId());

        assertThat(about.size())
                .as("історія на порушника — головний аргумент при рішенні про бан")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("на себе поскаржитись не можна")
    void cannotReportYourself() throws Exception {
        mvc.perform(post("/api/complaints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedUserId\":" + reporter.getUserId()
                                + ",\"reason\":\"сам на себе\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("скаргу без причини не приймають")
    void reasonIsRequired() throws Exception {
        mvc.perform(post("/api/complaints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedUserId\":" + offender.getUserId() + ",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("дейтер не бачить ні черги скарг, ні списку забанених")
    void daterCannotModerate() throws Exception {
        long complaintId = fileComplaint("щось сталось");

        mvc.perform(get("/api/admin/complaints").header(HttpHeaders.AUTHORIZATION, bearer(reporterToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/blocked").header(HttpHeaders.AUTHORIZATION, bearer(reporterToken)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/complaints/" + complaintId + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + offender.getUserId() + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"самосуд\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("адміністратора не можна ні забанити, ні видалити — інакше сервіс лишиться без модерації")
    void adminCannotBeBannedOrDeleted() throws Exception {
        User otherAdmin = newUser("Інший модератор", Role.ADMIN);

        mvc.perform(put("/api/admin/users/" + otherAdmin.getUserId() + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"спроба\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/users/" + otherAdmin.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("повторний бан того самого акаунта не проходить двічі")
    void cannotBanTwice() throws Exception {
        mvc.perform(put("/api/admin/users/" + offender.getUserId() + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"перший раз\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/admin/users/" + offender.getUserId() + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"другий раз\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── допоміжне ────────────────────────────────────────────────────────

    private long fileComplaint(String reason) throws Exception {
        return fileComplaintFrom(reporterToken, reason);
    }

    private long fileComplaintFrom(String token, String reason) throws Exception {
        MvcResult result = mvc.perform(post("/api/complaints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedUserId\":" + offender.getUserId()
                                + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("complaintId").asLong();
    }

    private JsonNode adminGet(String url) throws Exception {
        MvcResult result = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private JsonNode findById(JsonNode array, long complaintId) {
        return findById(array, "complaintId", complaintId);
    }

    private JsonNode findById(JsonNode array, String field, long id) {
        for (JsonNode node : array) {
            if (node.get(field).asLong() == id) {
                return node;
            }
        }
        return null;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private User newUser(String name, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordhash("{noop}irrelevant");
        u.setGender(Gender.FEMALE);
        u.setCity("Київ");
        u.setDatingGoal(DatingGoal.FRIENDSHIP);
        u.setDateOfBirth(LocalDate.of(1995, 5, 5));
        u.setRole(role);
        u.setBlocked(false);
        return userRepository.save(u);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
