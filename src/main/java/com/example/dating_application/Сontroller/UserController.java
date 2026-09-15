package com.example.dating_application.Сontroller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.UserUpdateDTO;
import com.example.dating_application.DTO.Request.UserPatchDTO;
import com.example.dating_application.DTO.Response.PublicProfileDTO;
import java.util.stream.Collectors;
import java.util.List;
import com.example.dating_application.DTO.Response.UserResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.FeedService;
import com.example.dating_application.Service.SearchCriteria;
import com.example.dating_application.Service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;
    private final FeedService feedService;

    public UserController(UserService userService,  FeedService feedService) {
        this.userService = userService;
        this.feedService = feedService;
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        Long userId = getCurrentUserId();
        User user = userService.getById(userId);
        return ResponseEntity.ok(userService.toResponseDTO(user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@accessControlService.canViewProfile(#id)")
    public ResponseEntity<PublicProfileDTO> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        return ResponseEntity.ok(userService.toPublicDTO(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@accessControlService.canEditProfile(#id)")
    public ResponseEntity<UserResponseDTO> updateProfile(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        User updatedUser = userService.updateProfile(id, dto);
        return ResponseEntity.ok(userService.toResponseDTO(updatedUser));
    }

    /**
     * Часткове оновлення анкети. Поля, яких немає в тілі, лишаються як були —
     * на відміну від PUT вище, який замінює профіль цілком і затирає пропущене.
     * Фронту слід користуватися саме цим ендпоінтом; PUT лишено для повної заміни.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("@accessControlService.canEditProfile(#id)")
    public ResponseEntity<UserResponseDTO> patchProfile(@PathVariable Long id,
                                                        @Valid @RequestBody UserPatchDTO dto) {
        User updatedUser = userService.patchProfile(id, dto);
        return ResponseEntity.ok(userService.toResponseDTO(updatedUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@accessControlService.canDeleteAccount(#id)")
    public ResponseEntity<Map<String, Object>> deleteProfile(@PathVariable Long id) {
        userService.deleteUser(id);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User account deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/next")
    public ResponseEntity<PublicProfileDTO> getNextProfile(
            @RequestParam(required = false) com.example.dating_application.Entity.Gender gender,
            @RequestParam(required = false) @Min(value = 18, message = "minAge must be at least 18")
            @Max(value = 120, message = "minAge must not exceed 120") Integer minAge,
            @RequestParam(required = false) @Min(value = 18, message = "maxAge must be at least 18")
            @Max(value = 120, message = "maxAge must not exceed 120") Integer maxAge,
            @RequestParam(required = false) @Size(max = 100, message = "city must not exceed 100 characters") String city,
            @RequestParam(required = false) com.example.dating_application.Entity.DatingGoal datingGoal) {

        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new BusinessException("minAge must not be greater than maxAge");
        }

        Long currentUserId = getCurrentUserId();

        SearchCriteria criteria = new SearchCriteria();
        criteria.setGender(gender);
        criteria.setMinAge(minAge);
        criteria.setMaxAge(maxAge);
        criteria.setCity(city);
        criteria.setDatingGoal(datingGoal);

        User profile = feedService.getNextProfile(currentUserId, criteria);
        return ResponseEntity.ok(userService.toPublicDTO(profile));
    }

    /**
     * Кілька наступних анкет одним запитом — для смуги «далі у стрічці».
     *
     * Це НЕ заміна {@code /next}: рішення (лайк чи пропуск) приймається лише по
     * поточній анкеті, а список потрібен, щоб показати, хто йде за нею. Добір,
     * фільтри й ранжування ті самі, тож перший елемент завжди дорівнює тому,
     * що віддає {@code /next}.
     */
    @GetMapping("/feed")
    public ResponseEntity<List<PublicProfileDTO>> getUpcoming(
            @RequestParam(required = false) com.example.dating_application.Entity.Gender gender,
            @RequestParam(required = false) @Min(value = 18, message = "minAge must be at least 18")
            @Max(value = 120, message = "minAge must not exceed 120") Integer minAge,
            @RequestParam(required = false) @Min(value = 18, message = "maxAge must be at least 18")
            @Max(value = 120, message = "maxAge must not exceed 120") Integer maxAge,
            @RequestParam(required = false) @Size(max = 100, message = "city must not exceed 100 characters") String city,
            @RequestParam(required = false) com.example.dating_application.Entity.DatingGoal datingGoal,
            @RequestParam(required = false, defaultValue = "5")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 20, message = "limit must not exceed 20") int limit) {

        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new BusinessException("minAge must not be greater than maxAge");
        }

        Long currentUserId = getCurrentUserId();

        SearchCriteria criteria = new SearchCriteria();
        criteria.setGender(gender);
        criteria.setMinAge(minAge);
        criteria.setMaxAge(maxAge);
        criteria.setCity(city);
        criteria.setDatingGoal(datingGoal);

        List<PublicProfileDTO> profiles = feedService.getUpcomingProfiles(currentUserId, criteria, limit)
                .stream()
                .map(userService::toPublicDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(profiles);
    }
}


