package com.example.dating_application.DTO.Response;

import java.util.List;

/**
 * Квитанція прочитання: користувач readerId прочитав повідомлення messageIds у чаті chatId.
 */
public class ReadReceiptDTO {
    private Long chatId;
    private Long readerId;
    private List<Long> messageIds;

    public ReadReceiptDTO() {
    }

    public ReadReceiptDTO(Long chatId, Long readerId, List<Long> messageIds) {
        this.chatId = chatId;
        this.readerId = readerId;
        this.messageIds = messageIds;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getReaderId() {
        return readerId;
    }

    public void setReaderId(Long readerId) {
        this.readerId = readerId;
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }
}