package com.example.dating_application.Security;

import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Охорона WebSocket — єдині двері до real-time частини застосунку.
 *
 * Рукостискання йде ПОВЗ звичайний ланцюг Spring Security (браузер не може
 * додати заголовок Authorization до WebSocket-запиту), тож усі перевірки, які
 * для REST робить JwtFilter, тут доводиться робити заново. Пропущена перевірка
 * означає не «сторінка не відкрилась», а «забанений далі читає повідомлення
 * в реальному часі».
 */
@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String JTI = "jti-123";
    private static final String EMAIL = "she@example.com";

    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepository userRepository;
    @Mock private TokenRevocationService tokenRevocationService;

    private JwtHandshakeInterceptor interceptor;
    private MockHttpServletRequest servletRequest;
    private MockHttpServletResponse servletResponse;
    private Map<String, Object> attributes;
    private User user;

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor(jwtUtil, userRepository, tokenRevocationService);
        servletRequest = new MockHttpServletRequest("GET", "/ws/chat");
        servletResponse = new MockHttpServletResponse();
        attributes = new HashMap<>();

        user = new User();
        user.setUserId(42L);
        user.setEmail(EMAIL);
        user.setBlocked(false);

        lenient().when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        lenient().when(jwtUtil.getJtiFromToken(TOKEN)).thenReturn(JTI);
        lenient().when(jwtUtil.getEmailFromToken(TOKEN)).thenReturn(EMAIL);
        lenient().when(tokenRevocationService.isRevoked(JTI)).thenReturn(false);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("з чинним токеном рукостискання проходить і запам'ятовує, хто підключився")
    void validTokenOpensConnection() {
        servletRequest.setParameter("token", TOKEN);

        assertThat(handshake()).isTrue();
        assertThat(attributes.get(JwtHandshakeInterceptor.USER_ID_ATTR))
                .as("далі за цим id хендлер шукає, кому слати пуші")
                .isEqualTo(42L);
        assertThat(attributes.get(JwtHandshakeInterceptor.JTI_ATTR))
                .as("jti потрібен, щоб вихід гасив саме цю сесію")
                .isEqualTo(JTI);
    }

    @Test
    @DisplayName("префікс «Bearer » у параметрі допускається")
    void acceptsBearerPrefix() {
        servletRequest.setParameter("token", "Bearer " + TOKEN);

        assertThat(handshake()).isTrue();
        assertThat(attributes).containsKey(JwtHandshakeInterceptor.USER_ID_ATTR);
    }

    @Test
    @DisplayName("без токена з'єднання не відкривається")
    void rejectsMissingToken() {
        assertThat(handshake()).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("порожній токен так само не пускає")
    void rejectsBlankToken() {
        servletRequest.setParameter("token", "   ");

        assertThat(handshake()).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("підроблений або протухлий токен не пускає")
    void rejectsInvalidToken() {
        servletRequest.setParameter("token", "підробка");
        when(jwtUtil.isTokenValid("підробка")).thenReturn(false);

        assertThat(handshake()).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("токен, з яким уже вийшли, нового з'єднання не відкриває (FR-5)")
    void rejectsRevokedToken() {
        servletRequest.setParameter("token", TOKEN);
        when(tokenRevocationService.isRevoked(JTI)).thenReturn(true);

        assertThat(handshake())
                .as("інакше вихід гасив би лише REST, а сокет лишався б жити")
                .isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("забанений не підключається навіть із формально чинним токеном")
    void rejectsBannedUser() {
        servletRequest.setParameter("token", TOKEN);
        user.setBlocked(true);

        assertThat(handshake())
                .as("це головне: бан має діяти й на real-time, не лише на REST")
                .isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("токен видаленого користувача не пускає")
    void rejectsUnknownUser() {
        servletRequest.setParameter("token", TOKEN);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(handshake()).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("не-сервлетний запит відхиляється: витягти параметр з нього нічим")
    void rejectsNonServletRequest() {
        ServerHttpRequest foreign = mock(ServerHttpRequest.class);
        ServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        boolean allowed = interceptor.beforeHandshake(
                foreign, response, mock(WebSocketHandler.class), attributes);

        assertThat(allowed).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("afterHandshake нічого не робить і ні на що не скаржиться")
    void afterHandshakeIsHarmless() {
        servletRequest.setParameter("token", TOKEN);

        interceptor.afterHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                null);

        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    private boolean handshake() {
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                attributes);
    }
}
