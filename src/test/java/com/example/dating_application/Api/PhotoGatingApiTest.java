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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ЯДРО ПРОДУКТУ (FR-14.1): фото відкриваються ТІЛЬКИ після взаємної симпатії.
 *
 * Перевіряється через справжній ланцюг запиту: JWT-фільтр → Spring Security →
 * контролер → правило доступу → диск. Модульний тест тут не допоміг би: частина
 * умови живе в запитах до БД, а частина захисту — у фільтрах.
 *
 * MockMvc, а не HTTP-клієнт: у Boot 4 TestRestTemplate тягне окремий модуль
 * spring-boot-restclient, якого в проєкті немає, а фільтри MockMvc проходить ті самі.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoGatingApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper json;

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
    @DisplayName("без метчу фото співрозмовника не віддаються")
    void photosAreHiddenWithoutMatch() throws Exception {
        uploadPhoto(bohdanToken);

        assertThat(photoCount(bohdan, aliceToken))
                .as("це і є головне правило застосунку")
                .isZero();
    }

    @Test
    @DisplayName("після взаємної симпатії фото відкриваються обом")
    void photosOpenAfterMutualLike() throws Exception {
        uploadPhoto(bohdanToken);
        uploadPhoto(aliceToken);

        makeMatch();

        assertThat(photoCount(bohdan, aliceToken)).as("Аліна бачить фото Богдана").isEqualTo(1);
        assertThat(photoCount(alice, bohdanToken)).as("відкривається обом одночасно").isEqualTo(1);
    }

    @Test
    @DisplayName("свої фото видно завжди, без жодного метчу")
    void ownPhotosAreAlwaysVisible() throws Exception {
        uploadPhoto(aliceToken);

        assertThat(photoCount(alice, aliceToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("блокування після метчу знову закриває фото")
    void blockClosesPhotosAgain() throws Exception {
        uploadPhoto(bohdanToken);
        makeMatch();
        assertThat(photoCount(bohdan, aliceToken)).isEqualTo(1);

        mvc.perform(post("/api/blocks/" + bohdan.getUserId()).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(photoCount(bohdan, aliceToken))
                .as("блокування скасовує доступ, який дав метч")
                .isZero();
    }

    @Test
    @DisplayName("без токена API відповідає 401")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/photos/user/" + bohdan.getUserId() + "/content"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("дейтер не має доступу до адмінських ендпоінтів")
    void daterCannotReachAdminApi() throws Exception {
        mvc.perform(get("/api/admin/complaints").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("підроблений токен не пускає далі фільтра")
    void forgedTokenIsRejected() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken + "tampered")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("чужий чат недоступний навіть із валідним токеном")
    void foreignChatIsForbidden() throws Exception {
        makeMatch();
        long chatId = firstChatId(aliceToken);

        User outsider = newUser("Стороння");
        mvc.perform(get("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken(outsider))))
                .andExpect(status().isForbidden());
    }

    // ── допоміжне ────────────────────────────────────────────────────────

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

    /** Взаємна симпатія через справжній API: лайк Богдана + прийняття Аліною. */
    private void makeMatch() throws Exception {
        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + alice.getUserId() + "}"))
                .andExpect(status().isOk());

        MvcResult incoming = mvc.perform(get("/api/requests/incoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode requests = json.readTree(incoming.getResponse().getContentAsString());
        long requestId = requests.get(0).get("requestId").asLong();

        mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
    }

    private void uploadPhoto(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", tinyPng());

        mvc.perform(multipart("/api/photos").file(file)
                        .param("isMain", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private int photoCount(User target, String viewerToken) throws Exception {
        MvcResult result = mvc.perform(get("/api/photos/user/" + target.getUserId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).size();
    }

    private long firstChatId(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/chats").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get(0).get("chatId").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Найменша картинка, яку бекенд усе ще погодиться обробити. */
    private static byte[] tinyPng() {
        try {
            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("не вдалося створити тестове зображення", e);
        }
    }
}
