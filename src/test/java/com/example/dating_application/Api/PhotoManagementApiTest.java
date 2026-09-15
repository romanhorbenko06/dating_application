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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Керування власними світлинами (FR-8.1): завантаження, головна, видалення,
 * віддача байтів. Доступ до ЧУЖИХ фото перевіряє PhotoGatingApiTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoManagementApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private User owner;
    private String token;

    @BeforeEach
    void setUp() {
        owner = newUser("Власниця");
        token = jwtUtil.generateToken(owner);
    }

    @Test
    @DisplayName("завантажене фото з'являється в списку зі своїм id")
    void uploadedPhotoAppearsInList() throws Exception {
        long photoId = upload(true);

        JsonNode photos = getJson("/api/photos/user/" + owner.getUserId());

        assertThat(photos.size()).isEqualTo(1);
        assertThat(photos.get(0).get("photoId").asLong()).isEqualTo(photoId);
        assertThat(photos.get(0).get("isMain").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("байти фото віддаються з типом і забороною кешування")
    void photoContentIsServedPrivately() throws Exception {
        long photoId = upload(true);

        MvcResult result = mvc.perform(get("/api/photos/" + photoId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(result.getResponse().getContentAsByteArray().length).isPositive();
        assertThat(result.getResponse().getHeader("Cache-Control"))
                .as("приватний контент: доступ може зникнути після блокування")
                .contains("no-store");
    }

    @Test
    @DisplayName("головною можна зробити іншу світлину — попередня перестає бути головною")
    void mainPhotoCanBeSwitched() throws Exception {
        long first = upload(true);
        long second = upload(false);

        mvc.perform(put("/api/photos/" + second + "/main").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        JsonNode photos = getJson("/api/photos/user/" + owner.getUserId());
        assertThat(mainFlag(photos, second)).isTrue();
        assertThat(mainFlag(photos, first)).as("двох головних бути не може").isFalse();
    }

    @Test
    @DisplayName("видалення головної передає роль наступній світлині")
    void deletingMainPromotesAnother() throws Exception {
        long main = upload(true);
        long other = upload(false);

        mvc.perform(delete("/api/photos/" + main).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        JsonNode photos = getJson("/api/photos/user/" + owner.getUserId());
        assertThat(photos.size()).isEqualTo(1);
        assertThat(mainFlag(photos, other))
                .as("анкета не має лишатись без аватарки")
                .isTrue();
    }

    @Test
    @DisplayName("більше шести світлин не приймається")
    void photoLimitIsEnforced() throws Exception {
        for (int i = 0; i < 6; i++) {
            upload(false);
        }

        mvc.perform(multipart("/api/photos").file(imagePart())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("файл, що не є зображенням, відхиляється")
    void nonImageIsRejected() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4 не картинка".getBytes());

        mvc.perform(multipart("/api/photos").file(pdf).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("запит без файлу пояснює, якої частини бракує")
    void missingFilePartIsExplained() throws Exception {
        MvcResult result = mvc.perform(multipart("/api/photos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("file");
    }

    @Test
    @DisplayName("чуже фото не видалити й не зробити головним")
    void cannotTouchSomeoneElsesPhoto() throws Exception {
        long mine = upload(true);
        String strangerToken = jwtUtil.generateToken(newUser("Стороння"));

        mvc.perform(delete("/api/photos/" + mine).header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/photos/" + mine + "/main").header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("дії з неіснуючим фото не дають 500")
    void unknownPhotoIsHandled() throws Exception {
        mvc.perform(delete("/api/photos/999999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/photos/999999/content").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("без токена фото недоступні")
    void photosRequireAuthentication() throws Exception {
        mvc.perform(get("/api/photos/user/" + owner.getUserId())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/photos").file(imagePart())).andExpect(status().isUnauthorized());
    }

    // ── допоміжне ────────────────────────────────────────────────────────

    private long upload(boolean main) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/photos").file(imagePart())
                        .param("isMain", String.valueOf(main))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("photoId").asLong();
    }

    private static MockMultipartFile imagePart() throws IOException {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray());
    }

    private static boolean mainFlag(JsonNode photos, long photoId) {
        for (JsonNode photo : photos) {
            if (photo.get("photoId").asLong() == photoId) {
                return photo.get("isMain").asBoolean();
            }
        }
        return false;
    }

    private JsonNode getJson(String url) throws Exception {
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
