package com.example.dating_application.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Вмикає @Scheduled. Потрібно для прибирання протухлих записів
 * у чорному списку токенів ({@code TokenRevocationService.purgeExpired}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
