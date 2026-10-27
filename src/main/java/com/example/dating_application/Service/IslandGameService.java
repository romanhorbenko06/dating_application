package com.example.dating_application.Service;

import com.example.dating_application.DTO.Response.IslandGameDTO;
import com.example.dating_application.DTO.Response.IslandItemDTO;
import com.example.dating_application.DTO.Response.IslandNightEventDTO;
import com.example.dating_application.DTO.Response.IslandPairCardDTO;
import com.example.dating_application.DTO.Response.IslandRoleDTO;
import com.example.dating_application.DTO.Response.IslandProposalDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.IslandGame;
import com.example.dating_application.Entity.IslandGameStatus;
import com.example.dating_application.Entity.IslandItem;
import com.example.dating_application.Entity.IslandNightEvent;
import com.example.dating_application.Entity.IslandPairStyle;
import com.example.dating_application.Entity.IslandRole;
import com.example.dating_application.Entity.IslandPlayer;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Repo.ChatRepository;
import com.example.dating_application.Repo.IslandGameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * «Острів на двох»: запрошення → вибір і здогад → розкриття → ніч → спільний список → острів.
 * Зміни партії йдуть під блокуванням рядка чату, щоб дві вкладки не створили дві партії.
 * noRollbackFor потрібен, щоб позначка EXPIRED пережила відмову.
 */
@Service
@Transactional(noRollbackFor = BusinessException.class)
public class IslandGameService {

    private static final Logger logger = LoggerFactory.getLogger(IslandGameService.class);

    public static final int ITEMS_PER_PICK = 3;

    public static final int MAX_KEEP = 4;

    private static final long EXPIRY_SWEEP_MS = 10 * 60 * 1000L;

    private final IslandGameRepository gameRepository;
    private final ChatRepository chatRepository;
    private final long inviteTtlHours;
    private final long phaseTtlHours;

    public IslandGameService(IslandGameRepository gameRepository,
                             ChatRepository chatRepository,
                             @Value("${app.island.invite-ttl-hours:48}") long inviteTtlHours,
                             @Value("${app.island.phase-ttl-hours:72}") long phaseTtlHours) {
        this.gameRepository = gameRepository;
        this.chatRepository = chatRepository;
        this.inviteTtlHours = inviteTtlHours;
        this.phaseTtlHours = phaseTtlHours;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<IslandItemDTO> catalog() {
        return Arrays.stream(IslandItem.values())
                .map(i -> new IslandItemDTO(i.name(), i.getTitle(), i.getTrait()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<IslandGame> findCurrent(Long chatId) {
        return gameRepository.findFirstByChatChatIdOrderByGameIdDesc(chatId);
    }

    /** Після відмови запросити знову може лише той, хто відмовив; дограний острів у чаті один. */
    public IslandGame invite(Long chatId, Long userId) {
        Chat chat = lockChat(chatId);
        User me = participant(chat, userId);
        LocalDateTime now = LocalDateTime.now();

        Optional<IslandGame> latest = gameRepository.findFirstByChatChatIdOrderByGameIdDesc(chatId);
        if (latest.isPresent()) {
            IslandGame prev = latest.get();
            expireIfOverdue(prev, now);
            switch (prev.getStatus()) {
                case INVITED, PICKING, NIGHT, SHORTLIST ->
                        throw new BusinessException("An island game is already in progress");
                case COMPLETED -> throw new BusinessException("You already have an island in this chat");
                case DECLINED -> {
                    if (prev.getInvitedBy().getUserId().equals(userId)) {
                        throw new BusinessException("Your invitation was declined; only your partner can start a new game");
                    }
                }
                case EXPIRED -> { /* можна знову */ }
            }
        }

        IslandGame game = new IslandGame();
        game.setChat(chat);
        game.setInvitedBy(me);
        game.setStatus(IslandGameStatus.INVITED);
        game.setCreatedAt(now);
        game.setExpiresAt(now.plusHours(inviteTtlHours));
        return gameRepository.save(game);
    }

    public IslandGame accept(Long chatId, Long userId) {
        IslandGame game = gameInPhase(chatId, userId, IslandGameStatus.INVITED, "Invitation has already been answered");
        if (game.getInvitedBy().getUserId().equals(userId)) {
            throw new BusinessException("Waiting for your partner to accept");
        }

        LocalDateTime now = LocalDateTime.now();
        game.setStatus(IslandGameStatus.PICKING);
        game.setAcceptedAt(now);
        game.setExpiresAt(now.plusHours(phaseTtlHours));
        return game;
    }

    public IslandGame decline(Long chatId, Long userId) {
        IslandGame game = gameInPhase(chatId, userId, IslandGameStatus.INVITED, "Invitation has already been answered");
        if (game.getInvitedBy().getUserId().equals(userId)) {
            throw new BusinessException("You cannot decline your own invitation");
        }

        game.setStatus(IslandGameStatus.DECLINED);
        game.setExpiresAt(null);
        return game;
    }

    public IslandGame submitPick(Long chatId, Long userId, List<IslandItem> items, String note) {
        validateThreeDistinct(items);
        IslandGame game = gameInPhase(chatId, userId, IslandGameStatus.PICKING, "The picking step is over");
        IslandPlayer me = playerOf(game, userId).orElseGet(() -> addPlayer(game, userId));

        if (me.hasPicked()) {
            throw new BusinessException("You have already made your pick");
        }
        replace(me.getPick(), items);
        me.setNote(normalizeSpaces(note));
        me.setPickedAt(LocalDateTime.now());

        revealIfReady(game);
        return gameRepository.save(game);
    }

    /** Здогад лише після власного вибору, щоб не підлаштувати набір під нього. */
    public IslandGame submitGuess(Long chatId, Long userId, List<IslandItem> items) {
        validateThreeDistinct(items);
        IslandGame game = gameInPhase(chatId, userId, IslandGameStatus.PICKING, "The guessing step is over");
        IslandPlayer me = playerOf(game, userId)
                .filter(IslandPlayer::hasPicked)
                .orElseThrow(() -> new BusinessException("Make your own pick first"));

        if (me.hasGuessed()) {
            throw new BusinessException("You have already made your guess");
        }
        replace(me.getGuess(), items);
        me.setGuessedAt(LocalDateTime.now());

        revealIfReady(game);
        return gameRepository.save(game);
    }

    private void revealIfReady(IslandGame game) {
        boolean ready = game.getPlayers().size() == 2
                && game.getPlayers().stream().allMatch(p -> p.hasPicked() && p.hasGuessed());
        if (!ready) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        IslandNightEvent[] events = IslandNightEvent.values();
        game.setNightEvent(events[ThreadLocalRandom.current().nextInt(events.length)]);
        game.setStatus(IslandGameStatus.NIGHT);
        game.setRevealedAt(now);
        game.setExpiresAt(now.plusHours(phaseTtlHours));
    }

    public IslandGame answerNight(Long chatId, Long userId, int choice) {
        IslandGame game = gameInPhase(chatId, userId, IslandGameStatus.NIGHT, "The night is over");
        if (choice < 0 || choice >= game.getNightEvent().getOptions().size()) {
            throw new BusinessException("Unknown answer");
        }
        IslandPlayer me = playerOf(game, userId)
                .orElseThrow(() -> new BusinessException("You are not a player of this game"));
        if (me.hasAnsweredNight()) {
            throw new BusinessException("You have already answered");
        }
        me.setNightChoice(choice);
        me.setNightAnsweredAt(LocalDateTime.now());

        if (game.getPlayers().stream().allMatch(IslandPlayer::hasAnsweredNight)) {
            LocalDateTime now = LocalDateTime.now();
            game.setStatus(IslandGameStatus.SHORTLIST);
            game.setNightRevealedAt(now);
            game.setExpiresAt(now.plusHours(phaseTtlHours));
        }
        return gameRepository.save(game);
    }

    /** Партія завершується, щойно пропозиції обох збіглися і за предметами, і за назвою. */
    public IslandGame propose(Long chatId, Long userId, List<IslandItem> items, String name) {
        IslandGame game = gameInPhase(chatId, userId, IslandGameStatus.SHORTLIST, "The shortlist step is over");
        List<IslandItem> pool = shortlistPool(game);
        int keep = keepCount(pool.size());

        if (items == null || items.size() != keep || items.contains(null)) {
            throw new BusinessException("Keep exactly " + keep + " items");
        }
        if (new HashSet<>(items).size() != items.size()) {
            throw new BusinessException("Items must be different");
        }
        if (!pool.containsAll(items)) {
            throw new BusinessException("You can only keep items that one of you picked");
        }
        String cleanName = normalizeSpaces(name);
        if (cleanName == null) {
            throw new BusinessException("Island name is required");
        }

        IslandPlayer me = playerOf(game, userId)
                .orElseThrow(() -> new BusinessException("You are not a player of this game"));
        replace(me.getProposalItems(), sortedByCatalog(items));
        me.setProposalName(cleanName);
        me.setProposedAt(LocalDateTime.now());
        if (me.getFirstProposalKey() == null) {
            me.setFirstProposalKey(IslandStyleRules.proposalKey(items, cleanName));
        }

        IslandPlayer partner = partnerPlayer(game, userId);
        boolean agreed = itemsAgreed(me, partner) && nameAgreed(me, partner);
        if (partner != null && partner.hasProposed() && !agreed) {
            game.setShortlistDisagreed(true);
        }
        if (agreed) {
            LocalDateTime now = LocalDateTime.now();
            replace(game.getFinalItems(), sortedByCatalog(items));
            game.setIslandName(cleanName);
            game.setStatus(IslandGameStatus.COMPLETED);
            game.setCompletedAt(now);
            game.setExpiresAt(null);
            fixPairCard(game, me, partner, IslandStyleRules.proposalKey(items, cleanName), now);
        }
        return gameRepository.save(game);
    }

    private void fixPairCard(IslandGame game, IslandPlayer a, IslandPlayer b, String finalKey, LocalDateTime now) {
        game.setPairStyle(IslandStyleRules.pairStyle(
                a.getPick(), b.getPick(),
                guessHits(a, b).size(), guessHits(b, a).size(),
                roleOf(game, a), roleOf(game, b)));
        game.setPairSignature(IslandStyleRules.signature(
                game.isShortlistDisagreed(), finalKey,
                a.getFirstProposalKey(), b.getFirstProposalKey(),
                Duration.between(game.getNightRevealedAt(), now)));
    }

    private static IslandRole roleOf(IslandGame game, IslandPlayer p) {
        return IslandStyleRules.roleOf(p.getPick(), game.getNightEvent().roleOf(p.getNightChoice()));
    }

    private static List<IslandItem> guessHits(IslandPlayer guesser, IslandPlayer target) {
        return guesser.getGuess().stream().filter(target.getPick()::contains).toList();
    }

    /** На один менше, ніж у пулі, але не більше MAX_KEEP. */
    public static int keepCount(int poolSize) {
        return Math.max(1, Math.min(MAX_KEEP, poolSize - 1));
    }

    @Scheduled(fixedDelay = EXPIRY_SWEEP_MS, initialDelay = EXPIRY_SWEEP_MS)
    public void expireStaleScheduled() {
        int n = expireStale(LocalDateTime.now());
        if (n > 0) {
            logger.info("Expired {} island game(s)", n);
        }
    }

    /** Окремо від @Scheduled, щоб тест міг підставити свій «зараз». */
    public int expireStale(LocalDateTime now) {
        List<IslandGame> stale = gameRepository.findByStatusInAndExpiresAtBefore(
                EnumSet.of(IslandGameStatus.INVITED, IslandGameStatus.PICKING,
                        IslandGameStatus.NIGHT, IslandGameStatus.SHORTLIST), now);
        stale.forEach(g -> {
            g.setStatus(IslandGameStatus.EXPIRED);
            g.setExpiresAt(null);
        });
        return stale.size();
    }

    /** Видимість визначають мітки часу, а не статус; протерміновану фазу одразу показуємо як EXPIRED. */
    public IslandGameDTO toView(IslandGame game, Long viewerId) {
        IslandGameDTO dto = new IslandGameDTO();
        dto.setGameId(game.getGameId());
        dto.setChatId(game.getChat().getChatId());
        dto.setInvitedById(game.getInvitedBy().getUserId());
        dto.setCreatedAt(game.getCreatedAt());
        dto.setCompletedAt(game.getCompletedAt());

        boolean overdue = isOverdue(game, LocalDateTime.now());
        dto.setStatus(overdue ? IslandGameStatus.EXPIRED : game.getStatus());
        dto.setExpiresAt(overdue ? null : game.getExpiresAt());

        IslandPlayer me = playerOf(game, viewerId).orElse(null);
        IslandPlayer partner = partnerPlayer(game, viewerId);

        if (me != null && me.hasPicked()) {
            dto.setMyPick(List.copyOf(me.getPick()));
            dto.setMyNote(me.getNote());
        }
        if (me != null && me.hasGuessed()) {
            dto.setMyGuess(List.copyOf(me.getGuess()));
        }
        dto.setPartnerPicked(partner != null && partner.hasPicked());
        dto.setPartnerGuessed(partner != null && partner.hasGuessed());

        if (game.getRevealedAt() != null && me != null && partner != null) {
            List<IslandItem> mine = me.getPick();
            List<IslandItem> theirs = partner.getPick();
            dto.setPartnerPick(List.copyOf(theirs));
            dto.setPartnerNote(partner.getNote());
            dto.setSharedItems(mine.stream().filter(theirs::contains).toList());
            dto.setPartnerGuess(List.copyOf(partner.getGuess()));
            dto.setMyGuessHits(guessHits(me, partner));
            dto.setPartnerGuessHits(guessHits(partner, me));
        }

        if (game.getNightEvent() != null) {
            IslandNightEvent e = game.getNightEvent();
            dto.setNightEvent(new IslandNightEventDTO(e.name(), e.getPrompt(), e.getOptions()));
        }
        if (me != null) {
            dto.setMyNightChoice(me.getNightChoice());
        }
        dto.setPartnerNightAnswered(partner != null && partner.hasAnsweredNight());
        if (game.getNightRevealedAt() != null && partner != null) {
            dto.setPartnerNightChoice(partner.getNightChoice());
        }

        if (game.getNightRevealedAt() != null) {
            List<IslandItem> pool = shortlistPool(game);
            dto.setShortlistPool(pool);
            dto.setKeepCount(keepCount(pool.size()));
            dto.setMyProposal(proposalOf(me));
            dto.setPartnerProposal(proposalOf(partner));
            dto.setItemsAgreed(itemsAgreed(me, partner));
            dto.setNameAgreed(nameAgreed(me, partner));
        }

        if (game.getStatus() == IslandGameStatus.COMPLETED) {
            dto.setFinalItems(List.copyOf(game.getFinalItems()));
            dto.setIslandName(game.getIslandName());
            if (game.getPairStyle() != null && me != null && partner != null) {
                dto.setPairCard(pairCard(game, me, partner));
            }
        }
        return dto;
    }

    private static IslandPairCardDTO pairCard(IslandGame game, IslandPlayer me, IslandPlayer partner) {
        IslandPairStyle style = game.getPairStyle();
        IslandPairCardDTO card = new IslandPairCardDTO();
        card.setStyleCode(style.name());
        card.setStyleTitle(style.getTitle());
        card.setStyleText(style.getText());
        card.setRare(style.isRare());
        if (game.getPairSignature() != null) {
            card.setSignatureCode(game.getPairSignature().name());
            card.setSignatureTitle(game.getPairSignature().getTitle());
        }
        card.setMyRole(roleDto(roleOf(game, me)));
        card.setPartnerRole(roleDto(roleOf(game, partner)));
        return card;
    }

    private static IslandRoleDTO roleDto(IslandRole role) {
        return new IslandRoleDTO(role.name(), role.getTitle(), role.getMeaning());
    }

    private Chat lockChat(Long chatId) {
        return chatRepository.findByIdForUpdate(chatId)
                .orElseThrow(() -> new BusinessException("Chat not found"));
    }

    private User participant(Chat chat, Long userId) {
        if (chat.getUser1().getUserId().equals(userId)) return chat.getUser1();
        if (chat.getUser2().getUserId().equals(userId)) return chat.getUser2();
        throw new BusinessException("You are not a participant of this chat");
    }

    /** Блокує чат, перевіряє учасника й фазу; прострочену фазу позначає EXPIRED. */
    private IslandGame gameInPhase(Long chatId, Long userId, IslandGameStatus phase, String wrongPhaseMessage) {
        Chat chat = lockChat(chatId);
        participant(chat, userId);

        IslandGame game = gameRepository.findFirstByChatChatIdOrderByGameIdDesc(chatId)
                .orElseThrow(() -> new BusinessException("No island game in this chat"));
        expireIfOverdue(game, LocalDateTime.now());
        if (game.getStatus() == IslandGameStatus.EXPIRED) {
            throw new BusinessException("The island game has expired");
        }
        if (game.getStatus() != phase) {
            throw new BusinessException(game.getStatus().isActive()
                    && game.getStatus().ordinal() < phase.ordinal()
                    ? "This step has not started yet"
                    : wrongPhaseMessage);
        }
        return game;
    }

    private IslandPlayer addPlayer(IslandGame game, Long userId) {
        IslandPlayer p = new IslandPlayer();
        p.setGame(game);
        p.setUser(participant(game.getChat(), userId));
        game.getPlayers().add(p);
        return p;
    }

    private void expireIfOverdue(IslandGame game, LocalDateTime now) {
        if (isOverdue(game, now)) {
            game.setStatus(IslandGameStatus.EXPIRED);
            game.setExpiresAt(null);
        }
    }

    private static boolean isOverdue(IslandGame game, LocalDateTime now) {
        return game.getStatus().isActive()
                && game.getExpiresAt() != null
                && game.getExpiresAt().isBefore(now);
    }

    private static Optional<IslandPlayer> playerOf(IslandGame game, Long userId) {
        return game.getPlayers().stream()
                .filter(p -> p.getUser().getUserId().equals(userId))
                .findFirst();
    }

    private static IslandPlayer partnerPlayer(IslandGame game, Long userId) {
        return game.getPlayers().stream()
                .filter(p -> !p.getUser().getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    /** У порядку каталогу, щоб пул був однаковий для обох гравців. */
    private static List<IslandItem> shortlistPool(IslandGame game) {
        Set<IslandItem> union = EnumSet.noneOf(IslandItem.class);
        game.getPlayers().forEach(p -> union.addAll(p.getPick()));
        return List.copyOf(union);
    }

    /** Змінюємо на місці: тут лежить колекція Hibernate, а незмінний список упаде на flush. */
    private static void replace(List<IslandItem> target, List<IslandItem> items) {
        target.clear();
        target.addAll(items);
    }

    private static List<IslandItem> sortedByCatalog(List<IslandItem> items) {
        return items.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static IslandProposalDTO proposalOf(IslandPlayer p) {
        if (p == null || !p.hasProposed()) {
            return null;
        }
        return new IslandProposalDTO(List.copyOf(p.getProposalItems()), p.getProposalName());
    }

    private static boolean itemsAgreed(IslandPlayer a, IslandPlayer b) {
        return a != null && b != null && a.hasProposed() && b.hasProposed()
                && new HashSet<>(a.getProposalItems()).equals(new HashSet<>(b.getProposalItems()));
    }

    private static boolean nameAgreed(IslandPlayer a, IslandPlayer b) {
        return a != null && b != null && a.hasProposed() && b.hasProposed()
                && a.getProposalName().toLowerCase(Locale.ROOT).equals(b.getProposalName().toLowerCase(Locale.ROOT));
    }

    private static String normalizeSpaces(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static void validateThreeDistinct(List<IslandItem> items) {
        if (items == null || items.size() != ITEMS_PER_PICK || items.contains(null)) {
            throw new BusinessException("Choose exactly " + ITEMS_PER_PICK + " items");
        }
        if (new HashSet<>(items).size() != ITEMS_PER_PICK) {
            throw new BusinessException("Items must be different");
        }
    }
}
