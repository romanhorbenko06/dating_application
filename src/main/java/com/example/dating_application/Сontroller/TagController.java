package com.example.dating_application.Сontroller;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.TagResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    /** Каталог усіх доступних тегів (фіксований, засівається при старті). */
    @GetMapping
    public ResponseEntity<List<TagResponseDTO>> getAllTags() {
        List<TagResponseDTO> tags = tagService.getAllTags().stream()
                .map(tagService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tags);
    }

    /** Теги (інтереси) поточного користувача. */
    @GetMapping("/my")
    public ResponseEntity<List<TagResponseDTO>> getMyTags() {
        Long currentUserId = getCurrentUserId();
        List<TagResponseDTO> tags = tagService.getUserTags(currentUserId).stream()
                .map(tagService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tags);
    }

    /**
     * Теги (інтереси) іншого користувача — частина його профілю, тож видимість
     * та сама, що й у GET /api/users/{id}: закрито при блоці в будь-якому напрямку
     * та для забанених адміном акаунтів.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("@accessControlService.canViewProfile(#userId)")
    public ResponseEntity<List<TagResponseDTO>> getUserTags(@PathVariable Long userId) {
        List<TagResponseDTO> tags = tagService.getUserTags(userId).stream()
                .map(tagService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tags);
    }

    /** Додати тег до свого профілю. */
    @PostMapping("/my/{tagId}")
    public ResponseEntity<Map<String, Object>> addTagToMe(@PathVariable Long tagId) {
        Long currentUserId = getCurrentUserId();
        tagService.addTagToUser(currentUserId, tagId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Tag added");
        return ResponseEntity.ok(response);
    }

    /** Прибрати тег зі свого профілю. */
    @DeleteMapping("/my/{tagId}")
    public ResponseEntity<Map<String, Object>> removeTagFromMe(@PathVariable Long tagId) {
        Long currentUserId = getCurrentUserId();
        tagService.removeTagFromUser(currentUserId, tagId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Tag removed");
        return ResponseEntity.ok(response);
    }
}