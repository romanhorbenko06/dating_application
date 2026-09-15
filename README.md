# Знайди — бекенд

Серверна частина дейтинг-застосунку: Spring Boot 4, PostgreSQL, JWT.
Головна механіка — **фотографії відкриваються лише після взаємної симпатії**:
доки збігу немає, стрічка й чужа анкета суто текстові.

Вебклієнт живе в окремому репозиторії — [dating_application_web](https://github.com/romanhorbenko06/dating_application_web).

## Що потрібно

- **JDK 21** (`java -version`)
- **PostgreSQL** із базою, що працює на `localhost:5432`
- Maven ставити не треба — у репозиторії є `mvnw`

## Запуск

**1. Створити бази.** Робочу й окрему для тестів:

```bash
createdb dating_application
createdb dating_application_test
```

За замовчуванням застосунок ходить у базу під користувачем `postgres`
(`spring.datasource.url` і `spring.datasource.username` в `application.properties`).

**2. Задати секрети.** У репозиторії їх свідомо немає: без ключа підпису
застосунок падає на старті з поясненням. Створи
`src/main/resources/application-local.properties` — цей файл у `.gitignore`:

```properties
# Ключ підпису JWT. Згенерувати:  openssl rand -base64 32
jwt.secret=<сюди згенерований ключ>

# Пароль до PostgreSQL (можна натомість задати змінну оточення DB_PASSWORD)
spring.datasource.password=<пароль>

# Адміністратор, який створиться при першому старті.
# Без пароля адміна просто не буде — з попередженням у лог.
admin.email=admin@datingapp.com
admin.password=<пароль адміна>
```

**3. Запустити:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Профіль `local` обов'язковий — саме він підтягує `application-local.properties`.
В IntelliJ IDEA: Run Configuration → Active profiles: `local`.

API підніметься на `http://localhost:8080`, Swagger — на
`http://localhost:8080/swagger-ui.html`.

## Пошта

За замовчуванням відправка **вимкнена** і застосунок повністю робочий без SMTP:
код підтвердження друкується в консоль блоком `VERIFICATION EMAIL (Development Mode)`.
Звідти його і беремо під час реєстрації.

Щоб слати справжні листи, додай в `application-local.properties`:

```properties
app.mail.enabled=true
spring.mail.username=<адреса Gmail>
spring.mail.password=<App Password, 16 символів>
```

`spring.mail.password` — це **App Password** Google, а не пароль від акаунта:
звичайний пароль SMTP не прийме. Пункт «App passwords» у налаштуваннях Google
з'являється лише після ввімкнення двофакторної автентифікації.

## Тести

```bash
./mvnw test
```

Тести йдуть проти окремої бази `dating_application_test` (профіль `test`,
схема створюється й зноситься на кожному прогоні), пошта вимкнена, ключ підпису
фіксований — жодних налаштувань не потрібно, лише щоб база існувала.

Звіт про покриття після прогону — `target/site/jacoco/index.html`.

## Дані, що лежать поза базою

Світлини зберігаються на диску, у теці `photos/` поруч із проєктом
(`app.photos.dir`, до 6 знімків на користувача). Тека в `.gitignore`.

## Налаштування, які може знадобитися змінити

| Властивість | За замовчуванням | Навіщо |
|---|---|---|
| `app.cors.allowed-origins` | `http://localhost:4200` | звідки браузеру дозволено звертатися до API |
| `app.frontend.url` | `http://localhost:4200` | адреса, на яку веде кнопка в листі з кодом |
| `jwt.expiration` | 7 днів | строк життя токена |
| `app.photos.max-per-user` | 6 | ліміт світлин |
| `app.ratelimit.*` | 15 хв / 5 спроб | захист від перебору й спам-розсилки |

Усі вони читаються зі змінних оточення, тож для іншого середовища нічого
редагувати в коді не треба.
