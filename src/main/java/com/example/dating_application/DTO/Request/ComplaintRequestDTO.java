package com.example.dating_application.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ComplaintRequestDTO {

    @NotNull(message = "reportedUserId is required")
    private Long reportedUserId;

    @NotBlank(message = "Reason is required")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    public ComplaintRequestDTO() {
    }

    public ComplaintRequestDTO(Long reportedUserId, String reason) {
        this.reportedUserId = reportedUserId;
        this.reason = reason;
    }

    public Long getReportedUserId() {
        return reportedUserId;
    }

    public void setReportedUserId(Long reportedUserId) {
        this.reportedUserId = reportedUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}