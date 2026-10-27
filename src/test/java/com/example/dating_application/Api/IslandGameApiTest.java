package com.example.dating_application.Api;

import com.example.dating_application.DTO.Response.IslandGameDTO;
import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.IslandGame;
import com.example.dating_application.Entity.IslandGameStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.IslandGameRepository;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Security.JwtUtil;
import com.example.dating_application.Service.IslandGameService;
import com.example.dating_application.Websocket.IslandRealtimeNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Острів на двох»: до розкриття ходи партнера ніде не видно, а гра не блокує чат. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IslandGameApiTest {

    private static final String ALICE_PICK = "[\"MATCHES\",\"GUITAR\",\"FLAMINGO\"]";
    private static final String BOHDAN_PICK = "[\"MATCHES\",\"GUITAR\",\"WATER_BARREL\"]";

    private static final String KEEP = "[\"MATCHES\",\"GUITAR\",\"FLAMINGO\"]";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private IslandGameRepository gameRepository;
    @Autowired private IslandGameService gameService;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private IslandRealtimeNotifier realtimeNotifier;

    private User alice;
    private User bohdan;
    private String aliceToken;
    private String bohdanToken;
    private long chatId;

    @BeforeEach
    void setUp() throws Exception {
        alice = newUser("Аліна", Role.DATER);
        bohdan = newUser("Богдан", Role.DATER);
        aliceToken = jwtUtil.generateToken(alice);
        bohdanToken = jwtUtil.generateToken(bohdan);
        chatId = createMatch();
    }

    @Test
    @DisplayName("повна партія: запрошення → вибір і здогад → ніч → спільний список → острів")
    void fullGameEndsWithAnIsland() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        assertThat(current(bohdanToken).get("status").asText()).isEqualTo("INVITED");
        JsonNode accepted = body(action(bohdanToken, "accept").andExpect(status().isOk()).andReturn());
        assertThat(accepted.get("status").asText()).isEqualTo("PICKING");

        JsonNode revealed = playRoundOne();
        assertThat(revealed.get("status").asText()).isEqualTo("NIGHT");
        assertThat(texts(revealed.get("partnerPick"))).containsExactly("MATCHES", "GUITAR", "FLAMINGO");
        assertThat(revealed.get("partnerNote").asText()).isEqualTo("з гідністю");
        assertThat(texts(revealed.get("sharedItems"))).containsExactly("MATCHES", "GUITAR");
        assertThat(revealed.get("nightEvent").get("options").size()).isEqualTo(4);

        night(aliceToken, 0).andExpect(status().isOk());
        JsonNode shortlist = body(night(bohdanToken, 2).andExpect(status().isOk()).andReturn());
        assertThat(shortlist.get("status").asText()).isEqualTo("SHORTLIST");
        assertThat(shortlist.get("partnerNightChoice").asInt()).isZero();
        assertThat(texts(shortlist.get("shortlistPool")))
                .as("об'єднання двох наборів у порядку каталогу")
                .containsExactly("MATCHES", "WATER_BARREL", "GUITAR", "FLAMINGO");
        assertThat(shortlist.get("keepCount").asInt()).isEqualTo(3);

        propose(aliceToken, KEEP, "Сонячний Фламінго").andExpect(status().isOk());
        JsonNode done = body(propose(bohdanToken, KEEP, "  сонячний   фламінго ").andExpect(status().isOk()).andReturn());

        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(texts(done.get("finalItems"))).containsExactly("MATCHES", "GUITAR", "FLAMINGO");
        assertThat(done.get("islandName").asText())
                .as("регістр і пробіли не сперечаються; лишається написання того, хто погодився останнім")
                .isEqualTo("сонячний фламінго");
        assertThat(current(aliceToken).get("islandName").asText()).isEqualTo("сонячний фламінго");

        // Аліна: сірники + гітара + фламінго → Іскра; Богдан: сірники + гітара + вода → Господар
        JsonNode card = current(aliceToken).get("pairCard");
        assertThat(card.get("styleTitle").asText()).isEqualTo("Практики з душею");
        assertThat(card.get("rare").asBoolean()).isFalse();
        assertThat(card.get("myRole").get("title").asText()).isEqualTo("Іскра");
        assertThat(card.get("partnerRole").get("title").asText()).isEqualTo("Господар");
        assertThat(card.get("signatureTitle").asText())
                .as("без розбіжностей і за секунди")
                .isEqualTo("Швидко домовились");
        assertThat(current(bohdanToken).get("pairCard").get("myRole").get("title").asText())
                .as("у Богдана ролі дзеркальні")
                .isEqualTo("Господар");
    }

    @Test
    @DisplayName("стиль пари з'являється лише після фіналу")
    void pairCardOnlyAfterCompletion() throws Exception {
        reachShortlist();
        assertThat(current(aliceToken).get("pairCard").isNull()).isTrue();
    }

    @Test
    @DisplayName("здогад: видно, що саме кожен угадав про іншого — після розкриття")
    void guessHitsAreRevealed() throws Exception {
        startGame();
        JsonNode view = playRoundOne();

        // Богдан угадував Аліну: MATCHES, BOOK, FLAMINGO → влучив MATCHES і FLAMINGO
        assertThat(texts(view.get("myGuessHits"))).containsExactly("MATCHES", "FLAMINGO");
        // Аліна угадувала Богдана: GUITAR, SEEDS, HAMMOCK → влучила GUITAR
        assertThat(texts(view.get("partnerGuessHits"))).containsExactly("GUITAR");
        assertThat(texts(view.get("partnerGuess"))).containsExactly("GUITAR", "SEEDS", "HAMMOCK");
    }

    @Test
    @DisplayName("розкриття — лише коли ОБОЄ здали і вибір, і здогад")
    void revealWaitsForBothPicksAndGuesses() throws Exception {
        startGame();
        pick(aliceToken, ALICE_PICK).andExpect(status().isOk());
        pick(bohdanToken, BOHDAN_PICK).andExpect(status().isOk());
        guess(aliceToken, "[\"GUITAR\",\"SEEDS\",\"HAMMOCK\"]").andExpect(status().isOk());

        JsonNode aliceView = current(aliceToken);
        assertThat(aliceView.get("status").asText()).isEqualTo("PICKING");
        assertThat(aliceView.get("partnerPick").isNull())
                .as("обидва набори здано, але здогад Богдана ще ні — розкривати рано")
                .isTrue();
        assertThat(aliceView.get("partnerPicked").asBoolean()).isTrue();
        assertThat(aliceView.get("partnerGuessed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("вгадувати можна лише після власного вибору і лише раз")
    void guessComesAfterOwnPickAndOnlyOnce() throws Exception {
        startGame();
        guess(aliceToken, "[\"GUITAR\",\"SEEDS\",\"HAMMOCK\"]").andExpect(status().isBadRequest());

        pick(aliceToken, ALICE_PICK).andExpect(status().isOk());
        guess(aliceToken, "[\"GUITAR\",\"SEEDS\",\"HAMMOCK\"]").andExpect(status().isOk());
        guess(aliceToken, "[\"BOOK\",\"SEEDS\",\"HAMMOCK\"]").andExpect(status().isBadRequest());
        guess(aliceToken, "[\"BOOK\",\"BOOK\",\"HAMMOCK\"]").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ніч: відповідь партнера прихована, доки не відповіли обоє")
    void nightAnswerIsHiddenUntilBothAnswered() throws Exception {
        startGame();
        playRoundOne();
        night(aliceToken, 3).andExpect(status().isOk());

        JsonNode bohdanView = current(bohdanToken);
        assertThat(bohdanView.get("partnerNightAnswered").asBoolean()).isTrue();
        assertThat(bohdanView.get("partnerNightChoice").isNull()).isTrue();
        assertThat(bohdanView.get("shortlistPool").isNull()).isTrue();
    }

    @Test
    @DisplayName("ніч: варіант поза списком і повторна відповідь не приймаються; до ночі відповідати рано")
    void nightAnswerRules() throws Exception {
        startGame();
        night(aliceToken, 0).andExpect(status().isBadRequest());

        playRoundOne();
        night(aliceToken, 4).andExpect(status().isBadRequest());
        night(aliceToken, -1).andExpect(status().isBadRequest());
        night(aliceToken, 1).andExpect(status().isOk());
        night(aliceToken, 2).andExpect(status().isBadRequest());
        assertThat(current(aliceToken).get("myNightChoice").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("спільний список: рівно keepCount предметів і лише з об'єднання наборів")
    void shortlistMustFitThePool() throws Exception {
        reachShortlist();
        propose(aliceToken, "[\"MATCHES\",\"GUITAR\"]", "Острів").andExpect(status().isBadRequest());
        propose(aliceToken, "[\"MATCHES\",\"GUITAR\",\"BOOK\"]", "Острів").andExpect(status().isBadRequest());
        propose(aliceToken, "[\"MATCHES\",\"MATCHES\",\"GUITAR\"]", "Острів").andExpect(status().isBadRequest());
        propose(aliceToken, KEEP, "   ").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("пропозиції відкриті: партнер бачить мою, розбіжність не завершує, передумати можна")
    void proposalsAreOpenAndChangeable() throws Exception {
        reachShortlist();
        propose(aliceToken, KEEP, "Сонячний Фламінго").andExpect(status().isOk());
        JsonNode bohdanView = body(propose(bohdanToken, "[\"MATCHES\",\"WATER_BARREL\",\"GUITAR\"]", "Сонячний Фламінго")
                .andExpect(status().isOk()).andReturn());

        assertThat(bohdanView.get("status").asText()).isEqualTo("SHORTLIST");
        assertThat(texts(bohdanView.get("partnerProposal").get("items"))).containsExactly("MATCHES", "GUITAR", "FLAMINGO");
        assertThat(bohdanView.get("itemsAgreed").asBoolean()).isFalse();
        assertThat(bohdanView.get("nameAgreed").asBoolean()).isTrue();

        JsonNode done = body(propose(aliceToken, "[\"GUITAR\",\"WATER_BARREL\",\"MATCHES\"]", "Сонячний Фламінго")
                .andExpect(status().isOk()).andReturn());
        assertThat(done.get("status").asText())
                .as("Аліна поступилась водою замість фламінго — порядок у запиті не має значення")
                .isEqualTo("COMPLETED");
        assertThat(texts(done.get("finalItems"))).containsExactly("MATCHES", "WATER_BARREL", "GUITAR");
        assertThat(done.get("pairCard").get("signatureTitle").asText())
                .as("був торг, і фінал — стартовий варіант Богдана: Аліна поступилась")
                .isEqualTo("Сперечаєтесь, але з усмішкою");
    }

    @Test
    @DisplayName("скільки лишати: на один менше за пул, але не більше чотирьох")
    void keepCountRule() {
        assertThat(IslandGameService.keepCount(6)).isEqualTo(4);
        assertThat(IslandGameService.keepCount(5)).isEqualTo(4);
        assertThat(IslandGameService.keepCount(4)).isEqualTo(3);
        assertThat(IslandGameService.keepCount(3)).isEqualTo(2);
    }

    @Test
    @DisplayName("до розкриття видно лише ФАКТ ходу партнера, але не його предмети")
    void partnerPickIsHiddenUntilBothPicked() throws Exception {
        startGame();
        pick(aliceToken, ALICE_PICK).andExpect(status().isOk());

        JsonNode bohdanView = current(bohdanToken);
        assertThat(bohdanView.get("partnerPicked").asBoolean()).isTrue();
        assertThat(bohdanView.get("partnerPick").isNull()).isTrue();
        assertThat(bohdanView.get("sharedItems").isNull()).isTrue();
        assertThat(bohdanView.toString())
                .as("жодного предмета Аліни ніде в тілі відповіді")
                .doesNotContain("FLAMINGO");
    }

    @Test
    @DisplayName("WebSocket: кожен отримує СВІЙ вигляд, партнер не бачить чужого ходу до розкриття")
    void websocketDoesNotLeakPartnerPick() throws Exception {
        startGame();
        clearInvocations(realtimeNotifier);

        pick(aliceToken, ALICE_PICK).andExpect(status().isOk());

        ArgumentCaptor<IslandGameDTO> toBohdan = ArgumentCaptor.forClass(IslandGameDTO.class);
        verify(realtimeNotifier).notifyParticipant(eq(bohdan.getUserId()), toBohdan.capture());
        assertThat(toBohdan.getValue().isPartnerPicked()).isTrue();
        assertThat(toBohdan.getValue().getPartnerPick()).isNull();
        assertThat(toBohdan.getValue().getMyPick()).isNull();

        verify(realtimeNotifier, atLeastOnce()).notifyParticipant(eq(alice.getUserId()), any());
    }

    @Test
    @DisplayName("у чаті, де ще не грали, — 204")
    void noGameYieldsNoContent() throws Exception {
        mvc.perform(get("/api/chats/" + chatId + "/island").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("каталог: 12 предметів з назвою й описом")
    void catalogListsItems() throws Exception {
        JsonNode items = body(mvc.perform(get("/api/island/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk()).andReturn());
        assertThat(items.size()).isEqualTo(12);
        assertThat(items.get(0).get("code").asText()).isEqualTo("MATCHES");
        assertThat(items.get(0).get("title").asText()).isEqualTo("Сірники");
    }

    @Test
    @DisplayName("своє запрошення прийняти не можна")
    void inviterCannotAcceptOwnInvite() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        action(aliceToken, "accept").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ходити до прийняття запрошення не можна")
    void cannotPickBeforeAccept() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        pick(aliceToken, ALICE_PICK).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("набір: рівно три РІЗНІ відомі предмети")
    void pickMustBeThreeDistinctKnownItems() throws Exception {
        startGame();
        pick(aliceToken, "[\"MATCHES\",\"MATCHES\",\"GUITAR\"]").andExpect(status().isBadRequest());
        pick(aliceToken, "[\"MATCHES\",\"GUITAR\"]").andExpect(status().isBadRequest());
        pick(aliceToken, "[\"MATCHES\",\"GUITAR\",\"FLAMINGO\",\"BOOK\"]").andExpect(status().isBadRequest());
        pick(aliceToken, "[\"MATCHES\",\"GUITAR\",\"YACHT\"]").andExpect(status().isBadRequest());

        assertThat(current(bohdanToken).get("partnerPicked").asBoolean())
                .as("жоден невдалий хід не зарахований")
                .isFalse();
    }

    @Test
    @DisplayName("хід незмінний: вдруге походити не можна")
    void pickCannotBeChanged() throws Exception {
        startGame();
        pick(aliceToken, ALICE_PICK).andExpect(status().isOk());
        pick(aliceToken, "[\"BOOK\",\"HAMMOCK\",\"SEEDS\"]").andExpect(status().isBadRequest());
        assertThat(texts(current(aliceToken).get("myPick"))).containsExactly("MATCHES", "GUITAR", "FLAMINGO");
    }

    @Test
    @DisplayName("поки партія жива, другу не почати; дограний острів у чаті один")
    void oneGameAtATimeAndOneIslandPerChat() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        invite(bohdanToken).andExpect(status().isBadRequest());

        action(bohdanToken, "accept").andExpect(status().isOk());
        finishGame();

        invite(aliceToken).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("відмова не чіпає чат; знову запросити може лише той, хто відмовив")
    void declineKeepsChatAndOnlyDeclinerCanReinvite() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        JsonNode declined = body(action(bohdanToken, "decline").andExpect(status().isOk()).andReturn());
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");

        mvc.perform(post("/api/chats/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":" + chatId + ",\"content\":\"нічого, просто поговоримо\"}"))
                .andExpect(status().isOk());

        invite(aliceToken).andExpect(status().isBadRequest());
        invite(bohdanToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("своє запрошення «відхилити» не можна")
    void inviterCannotDeclineOwnInvite() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        action(aliceToken, "decline").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("протерміноване запрошення не прийняти, позначка EXPIRED лишається, можна запросити знову")
    void expiredInvitationCannotBeAccepted() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        pushDeadlineIntoPast();

        action(bohdanToken, "accept").andExpect(status().isBadRequest());
        assertThat(latestGame().getStatus())
                .as("відмова не відкотила позначку прострочення")
                .isEqualTo(IslandGameStatus.EXPIRED);

        invite(aliceToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ще не прибрану планувальником прострочену фазу вигляд показує як EXPIRED")
    void overdueGameLooksExpiredBeforeSweep() throws Exception {
        startGame();
        pushDeadlineIntoPast();

        JsonNode view = current(aliceToken);
        assertThat(view.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(view.get("expiresAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("планувальник гасить протерміновані фази і не чіпає живі")
    void sweepExpiresOnlyOverdueGames() throws Exception {
        startGame();
        gameService.expireStale(LocalDateTime.now());
        assertThat(latestGame().getStatus()).isEqualTo(IslandGameStatus.PICKING);

        gameService.expireStale(LocalDateTime.now().plusHours(73));
        assertThat(latestGame().getStatus()).isEqualTo(IslandGameStatus.EXPIRED);
    }

    @Test
    @DisplayName("сторонній, адмін і заблокований співрозмовник не грають — 403")
    void onlyParticipantsOfAccessibleChatPlay() throws Exception {
        String outsider = jwtUtil.generateToken(newUser("Стороння", Role.DATER));
        String admin = jwtUtil.generateToken(newUser("Модератор", Role.ADMIN));

        invite(outsider).andExpect(status().isForbidden());
        invite(admin).andExpect(status().isForbidden());

        mvc.perform(post("/api/blocks/" + alice.getUserId()).header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken)))
                .andExpect(status().isOk());
        invite(aliceToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("сповіщення: запрошення, кожен крок уперед — тому, хто чекав, і фінал")
    void gameEventsAreNotified() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        assertThat(notificationTypes(bohdanToken)).contains("ISLAND_INVITE");
        action(bohdanToken, "accept").andExpect(status().isOk());

        pick(bohdanToken, BOHDAN_PICK).andExpect(status().isOk());
        guess(bohdanToken, "[\"MATCHES\",\"BOOK\",\"FLAMINGO\"]").andExpect(status().isOk());
        pick(aliceToken, ALICE_PICK).andExpect(status().isOk());
        assertThat(notificationTypes(bohdanToken))
                .as("проміжний хід Аліни нікого не будить — гра ще не рушила")
                .doesNotContain("ISLAND_UPDATE");

        guess(aliceToken, "[\"GUITAR\",\"SEEDS\",\"HAMMOCK\"]").andExpect(status().isOk());
        assertThat(notificationTypes(bohdanToken))
                .as("Богдан походив першим і пішов — йому треба знати, що набори розкрито")
                .contains("ISLAND_UPDATE");

        night(aliceToken, 0).andExpect(status().isOk());
        night(bohdanToken, 1).andExpect(status().isOk());
        propose(aliceToken, KEEP, "Острів").andExpect(status().isOk());
        propose(bohdanToken, KEEP, "Острів").andExpect(status().isOk());

        assertThat(notificationTypes(aliceToken)).contains("ISLAND_COMPLETED");
        assertThat(notificationTypes(bohdanToken)).doesNotContain("ISLAND_COMPLETED");
        assertThat(notificationTypes(bohdanToken).stream().filter("ISLAND_UPDATE"::equals).count())
                .as("розкриття і пропозиція Аліни — одне непрочитане сповіщення гри на чат, а не стос")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("видалення акаунта з дограною партією не падає на зовнішніх ключах")
    void accountWithIslandCanBeDeleted() throws Exception {
        startGame();
        finishGame();

        mvc.perform(delete("/api/users/" + alice.getUserId()).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().is2xxSuccessful());
        assertThat(gameRepository.findFirstByChatChatIdOrderByGameIdDesc(chatId)).isEmpty();
    }

    private void startGame() throws Exception {
        invite(aliceToken).andExpect(status().isOk());
        action(bohdanToken, "accept").andExpect(status().isOk());
    }

    private JsonNode playRoundOne() throws Exception {
        pick(aliceToken, ALICE_PICK, "з гідністю").andExpect(status().isOk());
        pick(bohdanToken, BOHDAN_PICK).andExpect(status().isOk());
        guess(aliceToken, "[\"GUITAR\",\"SEEDS\",\"HAMMOCK\"]").andExpect(status().isOk());
        return body(guess(bohdanToken, "[\"MATCHES\",\"BOOK\",\"FLAMINGO\"]").andExpect(status().isOk()).andReturn());
    }

    private void reachShortlist() throws Exception {
        startGame();
        playRoundOne();
        night(aliceToken, 0).andExpect(status().isOk());
        night(bohdanToken, 1).andExpect(status().isOk());
    }

    private void finishGame() throws Exception {
        playRoundOne();
        night(aliceToken, 0).andExpect(status().isOk());
        night(bohdanToken, 1).andExpect(status().isOk());
        propose(aliceToken, KEEP, "Острів").andExpect(status().isOk());
        propose(bohdanToken, KEEP, "Острів").andExpect(status().isOk());
    }

    private void pushDeadlineIntoPast() {
        IslandGame game = latestGame();
        game.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        gameRepository.save(game);
    }

    private IslandGame latestGame() {
        return gameRepository.findFirstByChatChatIdOrderByGameIdDesc(chatId).orElseThrow();
    }

    private ResultActions invite(String token) throws Exception {
        return mvc.perform(post("/api/chats/" + chatId + "/island").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions action(String token, String name) throws Exception {
        return mvc.perform(post("/api/chats/" + chatId + "/island/" + name)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions pick(String token, String itemsJson) throws Exception {
        return pick(token, itemsJson, null);
    }

    private ResultActions pick(String token, String itemsJson, String note) throws Exception {
        String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
        return postJson(token, "picks", "{\"items\":" + itemsJson + noteJson + "}");
    }

    private ResultActions guess(String token, String itemsJson) throws Exception {
        return postJson(token, "guess", "{\"items\":" + itemsJson + "}");
    }

    private ResultActions night(String token, int choice) throws Exception {
        return postJson(token, "night", "{\"choice\":" + choice + "}");
    }

    private ResultActions propose(String token, String itemsJson, String name) throws Exception {
        return mvc.perform(put("/api/chats/" + chatId + "/island/shortlist")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":" + itemsJson + ",\"name\":\"" + name + "\"}"));
    }

    private ResultActions postJson(String token, String step, String content) throws Exception {
        return mvc.perform(post("/api/chats/" + chatId + "/island/" + step)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    private JsonNode current(String token) throws Exception {
        return body(mvc.perform(get("/api/chats/" + chatId + "/island")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn());
    }

    private List<String> notificationTypes(String token) throws Exception {
        JsonNode list = body(mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn());
        return texts(list, "type");
    }

    private long createMatch() throws Exception {
        mvc.perform(post("/api/requests/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bohdanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + alice.getUserId() + "}"))
                .andExpect(status().isOk());

        MvcResult incoming = mvc.perform(get("/api/requests/incoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        long requestId = body(incoming).get(0).get("requestId").asLong();

        MvcResult accepted = mvc.perform(post("/api/requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();
        return body(accepted).get("chatId").asLong();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> texts(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static List<String> texts(JsonNode array, String field) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(n -> n.get(field).asText()).toList();
    }

    private User newUser(String name, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordhash("{noop}irrelevant");
        u.setGender(Gender.FEMALE);
        u.setCity("Київ");
        u.setDatingGoal(DatingGoal.FRIENDSHIP);
        u.setDateOfBirth(LocalDate.of(1995, 5, 5));
        u.setRole(role);
        u.setBlocked(false);
        return userRepository.save(u);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
