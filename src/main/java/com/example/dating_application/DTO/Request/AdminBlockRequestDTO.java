package com.example.dating_application.DTO.Request;

import jakarta.validation.constraints.Size;

public class AdminBlockRequestDTO {

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}