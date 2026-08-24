package com.example.dating_application.DTO.Response;

import java.time.LocalDateTime;

/** Забанений адміністратором акаунт — віддається лише в адмін-панель. */
public class BlockedUserDTO {

    private Long userId;
    private String name;
    private String email;
    private LocalDateTime blockedAt;
    private String blockReason;

    public BlockedUserDTO(Long userId, String name, String email,
                          LocalDateTime blockedAt, String blockReason) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.blockedAt = blockedAt;
        this.blockReason = blockReason;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public String getBlockReason() {
        return blockReason;
    }
}