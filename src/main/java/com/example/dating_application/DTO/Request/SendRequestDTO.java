package com.example.dating_application.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SendRequestDTO {

    @NotNull(message = "toUserId is required")
    private Long toUserId;

    @Size(max = 500, message = "Message must not exceed 500 characters")
    private String message;

    public SendRequestDTO() {
    }

    public SendRequestDTO(Long toUserId, String message) {
        this.toUserId = toUserId;
        this.message = message;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}