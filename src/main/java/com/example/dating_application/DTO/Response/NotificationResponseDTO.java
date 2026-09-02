package com.example.dating_application.DTO.Response;

import com.example.dating_application.Entity.NotificationType;

import java.time.LocalDateTime;


public class NotificationResponseDTO {

    private Long notificationId;
    private NotificationType type;
    private Long actorId;
    private String actorName;
    private Long chatId;
    /** Скільки повідомлень згорнуто (для NEW_MESSAGE); для решти типів 1. */
    private Integer messageCount;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public NotificationResponseDTO() {
    }

    public NotificationResponseDTO(Long notificationId, NotificationType type, Long actorId,
                                   String actorName, Long chatId, Integer messageCount,
                                   Boolean isRead, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.type = type;
        this.actorId = actorId;
        this.actorName = actorName;
        this.chatId = chatId;
        this.messageCount = messageCount;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean read) {
        this.isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
