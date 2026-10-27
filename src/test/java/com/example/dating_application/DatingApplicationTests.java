package com.example.dating_application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Чи піднімається контекст. RANDOM_PORT, бо контейнер WebSocket є лише у справжньому Tomcat. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DatingApplicationTests {

	@Test
	void contextLoads() {
	}

}
