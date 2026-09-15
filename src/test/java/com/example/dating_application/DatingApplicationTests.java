package com.example.dating_application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Найдешевший з можливих тестів: чи піднімається контекст узагалі.
 *
 * Цінність не нульова — саме він ловить помилки, які видно лише при старті:
 * невірний JPQL у @Query (Hibernate розбирає їх при створенні репозиторіїв),
 * циклічні залежності бінів, відсутні обов'язкові властивості.
 */
/*
 * RANDOM_PORT, а не типовий MOCK: контейнер WebSocket існує лише в справжньому
 * Tomcat, і на mock-середовищі бін createWebSocketContainer падає з
 * «ServerContainer not found in ServletContext». Заразом це чесніша перевірка —
 * піднімається той самий шлях старту, що й у бою.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DatingApplicationTests {

	@Test
	void contextLoads() {
	}

}
