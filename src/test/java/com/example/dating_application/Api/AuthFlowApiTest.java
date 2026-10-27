package com.example.dating_application.Api;

import com.example.dating_application.Entity.VerificationCode;
import com.example.dating_application.Service.EmailService;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Repo.VerificationCodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Межові випадки реєстрації та входу (UC-01, UC-02, FR-5). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowApiTest {

    private static final String PASSWORD = "Passw0rd!23";

    @org.junit.jupiter.api.BeforeEach
    void stubCodeGeneration() {
        // Щоразу новий код, інакше не перевірити «надіслати ще раз».
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(100000);
        org.mockito.Mockito.lenient().when(emailService.generateVerificationCode())
                .thenAnswer(invocation -> String.valueOf(counter.incrementAndGet()));
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private VerificationCodeRepository verificationCodes;
    @Autowired private UserRepository userRepository;

    @MockitoBean private EmailService emailService;

    @Test
    @DisplayName("внутрішній збій не виносить своїх подробиць клієнту")
    void internalFailureDoesNotLeakDetails() throws Exception {
        when(emailService.generateVerificationCode())
                .thenThrow(new IllegalStateException(
                        "HikariPool-1 connection timeout at UserRepository.save"));

        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("leak-" + UUID.randomUUID() + "@test.local")))
                .andExpect(status().isBadRequest())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(body(result).get("message").asText())
                .isEqualTo("Registration failed. Please try again later");
        assertThat(response)
                .as("внутрішня діагностика лишається в лозі сервера, а не в тілі відповіді")
                .doesNotContain("HikariPool", "UserRepository", "IllegalState", "timeout");
    }

    @Test
    @DisplayName("повторний /register тією самою адресою видає НОВИЙ код замість помилки")
    void repeatedRegistrationIssuesFreshCode() throws Exception {
        String email = register();
        String first = codeFor(email);

        register(email);
        String second = codeFor(email);

        assertThat(second)
                .as("саме так фронт реалізує «надіслати код ще раз»")
                .isNotEqualTo(first);
        assertThat(verificationCodes.findByEmail(email)).isPresent();
    }

    @Test
    @DisplayName("зареєстрована адреса вдруге не приймається")
    void registeredEmailIsRejected() throws Exception {
        String email = register();
        verify(email, codeFor(email));

        MvcResult second = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(second).get("message").asText()).isEqualTo("Email already registered");
    }

    @Test
    @DisplayName("підтвердження без заявки на реєстрацію не проходить")
    void verifyWithoutRegistrationFails() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody("ніхто-" + UUID.randomUUID() + "@test.local", "123456")))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).get("message").asText()).contains("No verification code found");
    }

    @Test
    @DisplayName("протухлий код не приймається й прибирається з бази")
    void expiredCodeIsRejectedAndRemoved() throws Exception {
        String email = register();
        VerificationCode code = verificationCodes.findByEmail(email).orElseThrow();
        code.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        verificationCodes.save(code);

        MvcResult result = mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code.getCode())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).get("message").asText()).contains("expired");
        assertThat(verificationCodes.findByEmail(email))
                .as("протухлий запис не має лишатись: інакше ним не скористаєшся, а місце займає")
                .isEmpty();
    }

    @Test
    @DisplayName("дані анкети переносяться з реєстрації в акаунт")
    void profileDataSurvivesVerification() throws Exception {
        String email = register();
        verify(email, codeFor(email));

        String token = login(email);
        MvcResult me = mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode profile = body(me);
        assertThat(profile.get("name").asText()).isEqualTo("Новенька");
        assertThat(profile.get("city").asText()).isEqualTo("Київ");
        assertThat(profile.get("email").asText())
                .as("свою пошту власник бачить — на відміну від чужої анкети")
                .isEqualTo(email);
    }

    @Test
    @DisplayName("код спрацьовує лише один раз")
    void codeWorksOnlyOnce() throws Exception {
        String email = register();
        String code = codeFor(email);
        verify(email, code);

        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("вихід без заголовка Authorization — зрозуміла помилка")
    void logoutWithoutHeaderIsExplicit() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).get("message").asText()).contains("Bearer");
    }

    @Test
    @DisplayName("вихід із невалідним токеном не падає — розлогінення ідемпотентне")
    void logoutWithGarbageTokenIsHarmless() throws Exception {
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer сміття"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("реєстрація без обов'язкових полів дає помилки по полях")
    void registrationValidatesFields() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"не пошта\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode fieldErrors = body(result).get("fieldErrors");
        assertThat(fieldErrors).as("форма підсвічує саме ті поля, які назвав бекенд").isNotNull();
        assertThat(fieldErrors.has("email")).isTrue();
    }

    @Test
    @DisplayName("вхід із невірним паролем не пускає")
    void wrongPasswordIsRejected() throws Exception {
        String email = register();
        verify(email, codeFor(email));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"інший\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
    }

    private String register() throws Exception {
        return register(UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    }

    private String register(String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isOk());
        return email;
    }

    private void verify(String email, String code) throws Exception {
        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code)))
                .andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("token").asText().replace("Bearer ", "");
    }

    private String codeFor(String email) {
        return verificationCodes.findByEmail(email).orElseThrow().getCode();
    }

    private static String registerBody(String email) {
        return """
                {"email":"%s","name":"Новенька","gender":"FEMALE",
                 "dateOfBirth":"1995-05-05","city":"Київ","datingGoal":"FRIENDSHIP"}
                """.formatted(email);
    }

    private static String verifyBody(String email, String code) {
        return """
                {"email":"%s","password":"%s","verificationCode":"%s"}
                """.formatted(email, PASSWORD, code);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
