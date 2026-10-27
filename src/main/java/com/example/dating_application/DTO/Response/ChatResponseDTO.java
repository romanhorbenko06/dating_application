package com.example.dating_application.DTO.Response;

import java.time.LocalDate;

/** Лише публічні дані учасників, без email, ролі й статусу верифікації. */
public class ChatResponseDTO {
    private Long chatId;
    private Long user1Id;
    private String user1Name;
    private Long user2Id;
    private String user2Name;
    private LocalDate user1DateOfBirth;
    private String user1City;
    private LocalDate user2DateOfBirth;
    private String user2City;
    private long sharedTagCount;

    public ChatResponseDTO() {
    }

    public ChatResponseDTO(Long chatId, Long user1Id, String user1Name,
                           Long user2Id, String user2Name,
                           LocalDate user1DateOfBirth, String user1City,
                           LocalDate user2DateOfBirth, String user2City,
                           long sharedTagCount) {
        this.chatId = chatId;
        this.user1Id = user1Id;
        this.user1Name = user1Name;
        this.user2Id = user2Id;
        this.user2Name = user2Name;
        this.user1DateOfBirth = user1DateOfBirth;
        this.user1City = user1City;
        this.user2DateOfBirth = user2DateOfBirth;
        this.user2City = user2City;
        this.sharedTagCount = sharedTagCount;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getUser1Id() {
        return user1Id;
    }

    public void setUser1Id(Long user1Id) {
        this.user1Id = user1Id;
    }

    public String getUser1Name() {
        return user1Name;
    }

    public void setUser1Name(String user1Name) {
        this.user1Name = user1Name;
    }

    public Long getUser2Id() {
        return user2Id;
    }

    public void setUser2Id(Long user2Id) {
        this.user2Id = user2Id;
    }

    public String getUser2Name() {
        return user2Name;
    }

    public void setUser2Name(String user2Name) {
        this.user2Name = user2Name;
    }

    public LocalDate getUser1DateOfBirth() {
        return user1DateOfBirth;
    }

    public void setUser1DateOfBirth(LocalDate user1DateOfBirth) {
        this.user1DateOfBirth = user1DateOfBirth;
    }

    public String getUser1City() {
        return user1City;
    }

    public void setUser1City(String user1City) {
        this.user1City = user1City;
    }

    public LocalDate getUser2DateOfBirth() {
        return user2DateOfBirth;
    }

    public void setUser2DateOfBirth(LocalDate user2DateOfBirth) {
        this.user2DateOfBirth = user2DateOfBirth;
    }

    public String getUser2City() {
        return user2City;
    }

    public void setUser2City(String user2City) {
        this.user2City = user2City;
    }

    public long getSharedTagCount() {
        return sharedTagCount;
    }

    public void setSharedTagCount(long sharedTagCount) {
        this.sharedTagCount = sharedTagCount;
    }
}
