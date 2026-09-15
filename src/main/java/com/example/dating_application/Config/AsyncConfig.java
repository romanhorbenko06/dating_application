package com.example.dating_application.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Вмикає @Async. Потрібно для відправки листів ({@code EmailService}):
 * SMTP відповідає 1–3 секунди, і тримати на ньому HTTP-запит реєстрації немає сенсу.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Окремий пул саме під пошту — щоб зависання SMTP не з'їдало потоки,
     * потрібні іншій фоновій роботі.
     *
     * Черга обмежена (не Integer.MAX_VALUE за замовчуванням): якщо SMTP ліг,
     * задачі не мають накопичуватися до OOM. Переповнення обробляє CallerRuns —
     * лист піде в потоці HTTP-запиту, тобто реєстрація сповільниться, але не впаде.
     */
    @Bean
    public TaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Дати листам у черзі догоріти при зупинці застосунку
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
