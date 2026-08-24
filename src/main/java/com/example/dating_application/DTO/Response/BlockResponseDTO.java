package com.example.dating_application.DTO.Response;

import java.time.LocalDateTime;

/** Запис зі списку «мої блокування». */
public class BlockResponseDTO {

    private Long blockId;
    private Long blockedUserId;
    private String blockedUserName;
    private LocalDateTime createdAt;

    public BlockResponseDTO(Long blockId, Long blockedUserId, String blockedUserName, LocalDateTime createdAt) {
        this.blockId = blockId;
        this.blockedUserId = blockedUserId;
        this.blockedUserName = blockedUserName;
        this.createdAt = createdAt;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Long getBlockedUserId() {
        return blockedUserId;
    }

    public String getBlockedUserName() {
        return blockedUserName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}