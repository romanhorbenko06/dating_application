package com.example.dating_application.DTO.Response;

import com.example.dating_application.Entity.RequestStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class    RequestResponseDTO {
    private Long requestId;
    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String toUserName;
    private String message;
    private RequestStatus status;
    private LocalDateTime createdAt;
    /**
     * Подробиці про ВІДПРАВНИКА симпатії. Потрібні списку «вас вподобали»:
     * без них клієнт мусив би тягнути окрему анкету на кожен рядок.
     */
    private LocalDate fromUserDateOfBirth;
    private String fromUserCity;
    /** Скільки фото має відправник. Самі фото до взаємності лишаються закритими. */
    private long fromUserPhotoCount;
    /**
     * Скільки тем збігається у відправника й отримувача. Величина симетрична,
     * тож однаково правдива і для вхідних симпатій, і для надісланих.
     */
    private long sharedTagCount;

    public RequestResponseDTO() {
    }

    public RequestResponseDTO(Long requestId, Long fromUserId, String fromUserName,
                             Long toUserId, String toUserName, String message,
                             RequestStatus status, LocalDateTime createdAt,
                             LocalDate fromUserDateOfBirth, String fromUserCity,
                             long fromUserPhotoCount, long sharedTagCount) {
        this.requestId = requestId;
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.toUserId = toUserId;
        this.toUserName = toUserName;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.fromUserDateOfBirth = fromUserDateOfBirth;
        this.fromUserCity = fromUserCity;
        this.fromUserPhotoCount = fromUserPhotoCount;
        this.sharedTagCount = sharedTagCount;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getToUserName() {
        return toUserName;
    }

    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getFromUserDateOfBirth() {
        return fromUserDateOfBirth;
    }

    public void setFromUserDateOfBirth(LocalDate fromUserDateOfBirth) {
        this.fromUserDateOfBirth = fromUserDateOfBirth;
    }

    public String getFromUserCity() {
        return fromUserCity;
    }

    public void setFromUserCity(String fromUserCity) {
        this.fromUserCity = fromUserCity;
    }

    public long getFromUserPhotoCount() {
        return fromUserPhotoCount;
    }

    public void setFromUserPhotoCount(long fromUserPhotoCount) {
        this.fromUserPhotoCount = fromUserPhotoCount;
    }

    public long getSharedTagCount() {
        return sharedTagCount;
    }

    public void setSharedTagCount(long sharedTagCount) {
        this.sharedTagCount = sharedTagCount;
    }
}
