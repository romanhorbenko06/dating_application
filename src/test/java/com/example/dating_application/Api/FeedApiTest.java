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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Стрічка через API (FR-9…FR-12). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private User me;
    private String token;

    /** База між тестами не відкочується, тож кожен запуск живе у власному місті. */
    private String city;

    @BeforeEach
    void setUp() {
        city = "Місто-" + UUID.randomUUID().toString().substring(0, 8);
        me = newUser("Я", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1995);
        token = jwtUtil.generateToken(me);
    }

    private String feed(String path, String extraParams) {
        String params = "city=" + city + (extraParams.isEmpty() ? "" : "&" + extraParams);
        return path + "?" + params;
    }

    @Test
    @DisplayName("стрічка віддає анкету незнайомої людини")
    void nextReturnsAProfile() throws Exception {
        User stranger = newUser("Незнайомка", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1996);

        JsonNode profile = body(ok(feed("/api/users/next", "")));

        assertThat(profile.get("userId").asLong()).isEqualTo(stranger.getUserId());
        assertThat(profile.get("name").asText()).isEqualTo("Незнайомка");
    }

    @Test
    @DisplayName("анкета зі стрічки не містить пошти — це чужі персональні дані")
    void publicProfileHidesEmail() throws Exception {
        newUser("Незнайомка", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1996);

        JsonNode profile = body(ok(feed("/api/users/next", "")));

        assertThat(profile.has("email"))
                .as("пошту бачить лише власник анкети, у /api/users/me")
                .isFalse();
    }

    @Test
    @DisplayName("порожня стрічка каже про це прямо, а не віддає порожнечу")
    void emptyFeedIsExplicit() throws Exception {
        MvcResult result = mvc.perform(get(feed("/api/users/next", ""))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).get("message").asText()).contains("No more profiles");
    }

    @Test
    @DisplayName("той, кого я вже пропустив, у стрічці не повертається")
    void skippedProfileDoesNotComeBack() throws Exception {
        User first = newUser("Перша", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1996);
        User second = newUser("Друга", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1997);

        long shown = body(ok(feed("/api/users/next", ""))).get("userId").asLong();
        mvc.perform(post("/api/requests/skip/" + shown).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        long next = body(ok(feed("/api/users/next", ""))).get("userId").asLong();

        assertThat(next).isNotEqualTo(shown);
        assertThat(List.of(first.getUserId(), second.getUserId())).contains(next);
    }

    @Test
    @DisplayName("фільтри статі, міста й мети звужують стрічку")
    void filtersNarrowTheFeed() throws Exception {
        User wanted = newUser("Потрібний", Gender.MALE, city, DatingGoal.MARRIAGE, 1990);
        newUser("Зайва", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1990);

        JsonNode profile = body(ok(feed("/api/users/next", "gender=MALE&datingGoal=MARRIAGE")));

        assertThat(profile.get("userId").asLong()).isEqualTo(wanted.getUserId());
    }

    @Test
    @DisplayName("вік перетворюється на межі дат і справді фільтрує")
    void ageFilterWorksOnBothEnds() throws Exception {
        int currentYear = LocalDate.now().getYear();
        User young = newUser("Молода", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, currentYear - 20);
        User senior = newUser("Старша", Gender.FEMALE, city, DatingGoal.FRIENDSHIP, currentYear - 50);

        assertThat(body(ok(feed("/api/users/next", "minAge=40"))).get("userId").asLong())
                .isEqualTo(senior.getUserId());
        assertThat(body(ok(feed("/api/users/next", "maxAge=30"))).get("userId").asLong())
                .isEqualTo(young.getUserId());
    }

    @Test
    @DisplayName("суперечливий діапазон віку відхиляється зрозумілим поясненням")
    void contradictoryAgeRangeIsRejected() throws Exception {
        MvcResult result = mvc.perform(get("/api/users/next?minAge=40&maxAge=30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).get("message").asText())
                .contains("minAge must not be greater than maxAge");
    }

    @Test
    @DisplayName("вік поза межами 18..120 не приймається")
    void ageBoundsAreEnforced() throws Exception {
        mvc.perform(get("/api/users/next?minAge=17").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/next?maxAge=121").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("«далі у стрічці» віддає не більше за попрошене й починається з поточної анкети")
    void upcomingMatchesNextAndRespectsLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            newUser("Кандидат " + i, Gender.FEMALE, city, DatingGoal.FRIENDSHIP, 1990 + i);
        }

        JsonNode upcoming = body(ok(feed("/api/users/feed", "limit=3")));
        long next = body(ok(feed("/api/users/next", ""))).get("userId").asLong();

        assertThat(upcoming.size()).isEqualTo(3);
        assertThat(upcoming.get(0).get("userId").asLong())
                .as("перший у смузі — це рівно та анкета, яку показує /next")
                .isEqualTo(next);
    }

    @Test
    @DisplayName("limit поза межами 1..20 не приймається")
    void limitBoundsAreEnforced() throws Exception {
        mvc.perform(get("/api/users/feed?limit=0").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/feed?limit=21").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("невідоме значення енума дає 400, а не 500")
    void unknownEnumValueIsRejected() throws Exception {
        mvc.perform(get("/api/users/next?gender=ВИГАДАНА")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("без токена стрічка недоступна")
    void anonymousCannotBrowse() throws Exception {
        mvc.perform(get("/api/users/next")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/feed")).andExpect(status().isUnauthorized());
    }

    private MvcResult ok(String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private final List<User> created = new ArrayList<>();

    private User newUser(String name, Gender gender, String city, DatingGoal goal, int birthYear) {
        User u = new User();
        u.setName(name);
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordhash("{noop}irrelevant");
        u.setGender(gender);
        u.setCity(city);
        u.setDatingGoal(goal);
        u.setDateOfBirth(LocalDate.of(birthYear, 5, 5));
        u.setRole(Role.DATER);
        u.setBlocked(false);
        User saved = userRepository.save(u);
        created.add(saved);
        return saved;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
