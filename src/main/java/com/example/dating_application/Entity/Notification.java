package com.example.dating_application.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    /** Кому адресоване сповіщення. */
    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Хто спричинив подію (лайкнув, написав). */
    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /** Куди вести з кліку — для NEW_MATCH і NEW_MESSAGE; для NEW_LIKE чату ще немає. */
    private Long chatId;

    /**
     * Скільки повідомлень згорнуто в це сповіщення (лише для NEW_MESSAGE).
     * Рядок на кожне повідомлення означав би тисячі записів на одну розмову,
     * тож непрочитані повідомлення одного чату накопичуються в ОДНОМУ сповіщенні.
     * Для інших типів завжди 1.
     */
    @Column(nullable = false)
    private Integer messageCount = 1;

    @Column(nullable = false)
    private Boolean isRead = false;

    private LocalDateTime createdAt;

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
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
