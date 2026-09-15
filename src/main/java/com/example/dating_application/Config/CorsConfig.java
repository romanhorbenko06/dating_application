package com.example.dating_application.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Єдине місце, де налаштовано CORS.
 *
 * Раніше на кожному з дев'яти контролерів висів голий {@code @CrossOrigin}, і це
 * не працювало взагалі: {@code @CrossOrigin} обробляється у Spring MVC, тобто вже
 * ПІСЛЯ security-ланцюга, а preflight-запит OPTIONS приходить без Authorization
 * і впирався в {@code anyRequest().authenticated()} → 401. Браузер бачив збій
 * preflight і не слав основний запит. Працювали тільки {@code /api/auth/**}
 * (вони в permitAll) — через що виглядало, ніби «логін є, а решта чомусь ні».
 *
 * Тепер джерело істини одне, а {@code CustomWebSecurity} вмикає CORS у ланцюзі,
 * і preflight відпрацьовує ДО перевірки автентифікації.
 */
@Configuration
public class CorsConfig {

    /**
     * Дозволені origin'и через кому. Дефолт — типові порти Angular dev-server.
     * Прод-домен додається змінною оточення, без правки коду.
     *
     * Свідомо НЕ "*": застосунок працює з особистими даними, і список довірених
     * джерел має бути явним. Патерни (напр. https://*.example.com) теж підтримуються,
     * бо використовується allowedOriginPatterns.
     */
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

        // Токен їздить у заголовку Authorization, а не в куках, тож credentials
        // не потрібні. Лишаємо false: інакше довелося б відмовитись від патернів
        // і жорстко перелічувати origin'и.
        config.setAllowCredentials(false);

        // Заголовки, які фронту треба ЧИТАТИ. Без цього браузер їх ховає навіть
        // за успішної відповіді: зараз це Retry-After для 429 від rate limit.
        config.setExposedHeaders(List.of("Retry-After"));

        // Скільки браузер може кешувати результат preflight — менше зайвих OPTIONS.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
