package com.example.dating_application.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/** Єдине місце налаштування CORS; CustomWebSecurity вмикає його в ланцюзі, щоб preflight проходив до автентифікації. */
@Configuration
public class CorsConfig {

    /** Дозволені origin'и через кому; свідомо не "*". */
    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
                      List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        // Токен у заголовку, а не в куках, тож credentials не потрібні.
        config.setAllowCredentials(false);

        // Інакше браузер сховає Retry-After від фронту.
        config.setExposedHeaders(List.of("Retry-After"));

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
