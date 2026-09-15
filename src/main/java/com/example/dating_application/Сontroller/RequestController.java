package com.example.dating_application.Сontroller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.SendRequestDTO;
import com.example.dating_application.DTO.Response.RequestResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.NotificationService;
import com.example.dating_application.Service.RequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
@Validated
public class RequestController {

    /** Сторінка за замовчуванням. Раніше списки віддавались цілком. */
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final RequestService requestService;
    private final NotificationService notificationService;

    public RequestController(RequestService requestService,
                             NotificationService notificationService) {
        this.requestService = requestService;
        this.notificationService = notificationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @PostMapping("/send")
    public ResponseEntity<RequestResponseDTO> sendLike(@Valid @RequestBody SendRequestDTO dto) {
        Long currentUserId = getCurrentUserId();
        var result = requestService.send(currentUserId, dto);

        if (result.chat() != null) {
            // Лайк був зустрічним і став метчем — обом іде сповіщення про взаємність
            // (UC-08), а не про нову вхідну симпатію.
            notificationService.notifyNewMatch(result.chat());
        } else {
            // Сповіщення адресату (FR-19.2) — після збереження лайку
            notificationService.notifyNewLike(result.request().getToUser(),
                    result.request().getFromUser());
        }

        return ResponseEntity.ok(requestService.toResponseDTO(result.request()));
    }

    @PostMapping("/{requestId}/accept")
    @PreAuthorize("@accessControlService.canAcceptRequest(#requestId)")
    public ResponseEntity<Map<String, Object>> acceptLike(@PathVariable Long requestId) {
        Long currentUserId = getCurrentUserId();
        var chat = requestService.accept(requestId, currentUserId);

        // Взаємна симпатія — сповіщаємо обох (UC-08)
        notificationService.notifyNewMatch(chat);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Like accepted and chat created");
        response.put("chatId", chat.getChatId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("@accessControlService.canRejectRequest(#requestId)")
    public ResponseEntity<Map<String, Object>> rejectLike(@PathVariable Long requestId) {
        Long currentUserId = getCurrentUserId();
        requestService.reject(requestId, currentUserId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Like rejected");
        return ResponseEntity.ok(response);
    }

    /**
     * Лічильник для анкети: скільки людей вподобали цього користувача за весь час.
     * Окремий ендпоінт, а не довжина списку, бо список посторінковий — рахувати
     * за ним означало б або занижувати, або тягнути всі записи заради числа.
     */
    @GetMapping("/incoming/count")
    public ResponseEntity<Map<String, Long>> countIncomingLikes() {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(Map.of("count", requestService.countIncomingLikes(currentUserId)));
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<RequestResponseDTO>> getIncomingLikes(
            @RequestParam(required = false, defaultValue = "0")
            @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size) {
        Long currentUserId = getCurrentUserId();
        var requests = requestService.incoming(currentUserId, page, size);
        List<RequestResponseDTO> dtos = requests.stream()
                .map(requestService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/sent")
    public ResponseEntity<List<RequestResponseDTO>> getSentLikes(
            @RequestParam(required = false, defaultValue = "0")
            @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size) {
        Long currentUserId = getCurrentUserId();
        var requests = requestService.sent(currentUserId, page, size);
        List<RequestResponseDTO> dtos = requests.stream()
                .map(requestService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/skip/{userId}")
    public ResponseEntity<Map<String, Object>> skipProfile(@PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        requestService.skip(currentUserId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile skipped");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/skipped")
    public ResponseEntity<List<Map<String, Object>>> getSkippedProfiles(
            @RequestParam(required = false, defaultValue = "0")
            @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size) {
        Long currentUserId = getCurrentUserId();
        var requests = requestService.getSkipped(currentUserId, page, size);
        List<Map<String, Object>> skipped = requests.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", r.getToUser().getUserId());
                    m.put("name", r.getToUser().getName());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(skipped);
    }
}