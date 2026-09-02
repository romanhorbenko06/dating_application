package com.example.dating_application.Security;

import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Service.TokenRevocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;


@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "userId";

    public static final String JTI_ATTR = "jti";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil, UserRepository userRepository,
                                   TokenRevocationService tokenRevocationService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return reject(response);
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            return reject(response);
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (!jwtUtil.isTokenValid(token)) {
            return reject(response);
        }

        // Розлогінений токен не відкриває нове з'єднання (FR-5)
        String jti = jwtUtil.getJtiFromToken(token);
        if (tokenRevocationService.isRevoked(jti)) {
            return reject(response);
        }

        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return reject(response);
        }

        // Забаненого адміністратором не пускаємо навіть із валідним токеном
        if (Boolean.TRUE.equals(user.getBlocked())) {
            return reject(response);
        }

        attributes.put(USER_ID_ATTR, user.getUserId());
        attributes.put(JTI_ATTR, jti);
        return true;
    }

    private boolean reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // нічого не потрібно
    }
}