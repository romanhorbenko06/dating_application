package com.example.dating_application.Сontroller;

import com.example.dating_application.DTO.Request.IslandGuessRequestDTO;
import com.example.dating_application.DTO.Request.IslandNightRequestDTO;
import com.example.dating_application.DTO.Request.IslandPickRequestDTO;
import com.example.dating_application.DTO.Request.IslandShortlistRequestDTO;
import com.example.dating_application.DTO.Response.IslandGameDTO;
import com.example.dating_application.DTO.Response.IslandItemDTO;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.IslandGame;
import com.example.dating_application.Entity.IslandGameStatus;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Service.IslandGameService;
import com.example.dating_application.Service.NotificationService;
import com.example.dating_application.Websocket.IslandRealtimeNotifier;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** «Острів на двох». Партія адресується чатом: у чаті щонайбільше одна жива партія. */
@RestController
public class IslandController {

    private final IslandGameService islandGameService;
    private final IslandRealtimeNotifier realtimeNotifier;
    private final NotificationService notificationService;

    public IslandController(IslandGameService islandGameService,
                            IslandRealtimeNotifier realtimeNotifier,
                            NotificationService notificationService) {
        this.islandGameService = islandGameService;
        this.realtimeNotifier = realtimeNotifier;
        this.notificationService = notificationService;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new BusinessException("User not authenticated");
    }

    @GetMapping("/api/island/items")
    public ResponseEntity<List<IslandItemDTO>> items() {
        return ResponseEntity.ok(islandGameService.catalog());
    }

    /** 204 — у цьому чаті ще не грали. */
    @GetMapping("/api/chats/{chatId}/island")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> current(@PathVariable Long chatId) {
        Long me = currentUser().getUserId();
        return islandGameService.findCurrent(chatId)
                .map(g -> ResponseEntity.ok(islandGameService.toView(g, me)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/chats/{chatId}/island")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> invite(@PathVariable Long chatId) {
        User me = currentUser();
        IslandGame game = islandGameService.invite(chatId, me.getUserId());
        IslandGameDTO view = publish(game, me.getUserId());
        notificationService.notifyIslandInvite(game.getChat(), me);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/api/chats/{chatId}/island/accept")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> accept(@PathVariable Long chatId) {
        Long me = currentUser().getUserId();
        return ResponseEntity.ok(publish(islandGameService.accept(chatId, me), me));
    }

    @PostMapping("/api/chats/{chatId}/island/decline")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> decline(@PathVariable Long chatId) {
        Long me = currentUser().getUserId();
        return ResponseEntity.ok(publish(islandGameService.decline(chatId, me), me));
    }

    @PostMapping("/api/chats/{chatId}/island/picks")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> pick(@PathVariable Long chatId,
                                              @Valid @RequestBody IslandPickRequestDTO dto) {
        User me = currentUser();
        IslandGame game = islandGameService.submitPick(chatId, me.getUserId(), dto.getItems(), dto.getNote());
        return ResponseEntity.ok(afterMove(game, me, IslandGameStatus.NIGHT));
    }

    @PostMapping("/api/chats/{chatId}/island/guess")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> guess(@PathVariable Long chatId,
                                               @Valid @RequestBody IslandGuessRequestDTO dto) {
        User me = currentUser();
        IslandGame game = islandGameService.submitGuess(chatId, me.getUserId(), dto.getItems());
        return ResponseEntity.ok(afterMove(game, me, IslandGameStatus.NIGHT));
    }

    @PostMapping("/api/chats/{chatId}/island/night")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> night(@PathVariable Long chatId,
                                               @Valid @RequestBody IslandNightRequestDTO dto) {
        User me = currentUser();
        IslandGame game = islandGameService.answerNight(chatId, me.getUserId(), dto.getChoice());
        return ResponseEntity.ok(afterMove(game, me, IslandGameStatus.SHORTLIST));
    }

    @PutMapping("/api/chats/{chatId}/island/shortlist")
    @PreAuthorize("@accessControlService.canPlayIsland(#chatId)")
    public ResponseEntity<IslandGameDTO> shortlist(@PathVariable Long chatId,
                                                   @Valid @RequestBody IslandShortlistRequestDTO dto) {
        User me = currentUser();
        IslandGame game = islandGameService.propose(chatId, me.getUserId(), dto.getItems(), dto.getName());
        IslandGameDTO view = publish(game, me.getUserId());
        if (game.getStatus() == IslandGameStatus.COMPLETED) {
            notificationService.notifyIslandCompleted(game.getChat(), me);
        } else {
            notificationService.notifyIslandUpdate(game.getChat(), me);
        }
        return ResponseEntity.ok(view);
    }

    /** Сповіщення лише тоді, коли хід перевів гру в наступну фазу. */
    private IslandGameDTO afterMove(IslandGame game, User me, IslandGameStatus advancedTo) {
        IslandGameDTO view = publish(game, me.getUserId());
        if (game.getStatus() == advancedTo) {
            notificationService.notifyIslandUpdate(game.getChat(), me);
        }
        return view;
    }

    private IslandGameDTO publish(IslandGame game, Long actorId) {
        Chat chat = game.getChat();
        IslandGameDTO actorView = null;
        for (User participant : List.of(chat.getUser1(), chat.getUser2())) {
            Long id = participant.getUserId();
            IslandGameDTO view = islandGameService.toView(game, id);
            realtimeNotifier.notifyParticipant(id, view);
            if (id.equals(actorId)) {
                actorView = view;
            }
        }
        return actorView;
    }
}
