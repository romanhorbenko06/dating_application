package com.example.dating_application.DTO.Response;

import java.time.LocalDateTime;

/**
 * Безпечне представлення повідомлення — відправник лише як id + ім'я,
 * без email/ролі/статусу верифікації.
 */
public class MessageResponseDTO {
    private Long messageId;
    private Long chatId;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime sentAt;
    private Boolean isRead;
    /** null — не редагувалось; інакше час останньої правки. */
    private LocalDateTime editedAt;
    /** true — повідомлення видалене автором; content у такому разі null. */
    private Boolean isDeleted;

    public MessageResponseDTO() {
    }

    public MessageResponseDTO(Long messageId, Long chatId, Long senderId, String senderName,
                              String content, LocalDateTime sentAt, Boolean isRead,
                              LocalDateTime editedAt, Boolean isDeleted) {
        this.messageId = messageId;
        this.chatId = chatId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.sentAt = sentAt;
        this.isRead = isRead;
        this.editedAt = editedAt;
        this.isDeleted = isDeleted;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean read) {
        isRead = read;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean deleted) {
        isDeleted = deleted;
    }
}