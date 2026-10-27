package com.example.dating_application.DTO.Response;

import com.example.dating_application.Entity.IslandGameStatus;
import com.example.dating_application.Entity.IslandItem;

import java.time.LocalDateTime;
import java.util.List;

/** Партія очима конкретного гравця: зміст ходів партнера видно лише після того, як походили обоє. */
public class IslandGameDTO {
    private Long gameId;
    private Long chatId;
    private IslandGameStatus status;
    private Long invitedById;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private List<IslandItem> myPick;
    private String myNote;
    private boolean partnerPicked;
    private List<IslandItem> partnerPick;
    private String partnerNote;
    private List<IslandItem> sharedItems;
    private List<IslandItem> myGuess;
    private boolean partnerGuessed;
    private List<IslandItem> partnerGuess;
    private List<IslandItem> myGuessHits;
    private List<IslandItem> partnerGuessHits;
    private IslandNightEventDTO nightEvent;
    private Integer myNightChoice;
    private boolean partnerNightAnswered;
    private Integer partnerNightChoice;
    private List<IslandItem> shortlistPool;
    private Integer keepCount;
    private IslandProposalDTO myProposal;
    private IslandProposalDTO partnerProposal;
    private boolean itemsAgreed;
    private boolean nameAgreed;
    private List<IslandItem> finalItems;
    private String islandName;
    /** Лише в COMPLETED. */
    private IslandPairCardDTO pairCard;

    public IslandGameDTO() {
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public IslandGameStatus getStatus() {
        return status;
    }

    public void setStatus(IslandGameStatus status) {
        this.status = status;
    }

    public Long getInvitedById() {
        return invitedById;
    }

    public void setInvitedById(Long invitedById) {
        this.invitedById = invitedById;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<IslandItem> getMyPick() {
        return myPick;
    }

    public void setMyPick(List<IslandItem> myPick) {
        this.myPick = myPick;
    }

    public String getMyNote() {
        return myNote;
    }

    public void setMyNote(String myNote) {
        this.myNote = myNote;
    }

    public boolean isPartnerPicked() {
        return partnerPicked;
    }

    public void setPartnerPicked(boolean partnerPicked) {
        this.partnerPicked = partnerPicked;
    }

    public List<IslandItem> getPartnerPick() {
        return partnerPick;
    }

    public void setPartnerPick(List<IslandItem> partnerPick) {
        this.partnerPick = partnerPick;
    }

    public String getPartnerNote() {
        return partnerNote;
    }

    public void setPartnerNote(String partnerNote) {
        this.partnerNote = partnerNote;
    }

    public List<IslandItem> getSharedItems() {
        return sharedItems;
    }

    public void setSharedItems(List<IslandItem> sharedItems) {
        this.sharedItems = sharedItems;
    }

    public List<IslandItem> getMyGuess() {
        return myGuess;
    }

    public void setMyGuess(List<IslandItem> myGuess) {
        this.myGuess = myGuess;
    }

    public boolean isPartnerGuessed() {
        return partnerGuessed;
    }

    public void setPartnerGuessed(boolean partnerGuessed) {
        this.partnerGuessed = partnerGuessed;
    }

    public List<IslandItem> getPartnerGuess() {
        return partnerGuess;
    }

    public void setPartnerGuess(List<IslandItem> partnerGuess) {
        this.partnerGuess = partnerGuess;
    }

    public List<IslandItem> getMyGuessHits() {
        return myGuessHits;
    }

    public void setMyGuessHits(List<IslandItem> myGuessHits) {
        this.myGuessHits = myGuessHits;
    }

    public List<IslandItem> getPartnerGuessHits() {
        return partnerGuessHits;
    }

    public void setPartnerGuessHits(List<IslandItem> partnerGuessHits) {
        this.partnerGuessHits = partnerGuessHits;
    }

    public IslandNightEventDTO getNightEvent() {
        return nightEvent;
    }

    public void setNightEvent(IslandNightEventDTO nightEvent) {
        this.nightEvent = nightEvent;
    }

    public Integer getMyNightChoice() {
        return myNightChoice;
    }

    public void setMyNightChoice(Integer myNightChoice) {
        this.myNightChoice = myNightChoice;
    }

    public boolean isPartnerNightAnswered() {
        return partnerNightAnswered;
    }

    public void setPartnerNightAnswered(boolean partnerNightAnswered) {
        this.partnerNightAnswered = partnerNightAnswered;
    }

    public Integer getPartnerNightChoice() {
        return partnerNightChoice;
    }

    public void setPartnerNightChoice(Integer partnerNightChoice) {
        this.partnerNightChoice = partnerNightChoice;
    }

    public List<IslandItem> getShortlistPool() {
        return shortlistPool;
    }

    public void setShortlistPool(List<IslandItem> shortlistPool) {
        this.shortlistPool = shortlistPool;
    }

    public Integer getKeepCount() {
        return keepCount;
    }

    public void setKeepCount(Integer keepCount) {
        this.keepCount = keepCount;
    }

    public IslandProposalDTO getMyProposal() {
        return myProposal;
    }

    public void setMyProposal(IslandProposalDTO myProposal) {
        this.myProposal = myProposal;
    }

    public IslandProposalDTO getPartnerProposal() {
        return partnerProposal;
    }

    public void setPartnerProposal(IslandProposalDTO partnerProposal) {
        this.partnerProposal = partnerProposal;
    }

    public boolean isItemsAgreed() {
        return itemsAgreed;
    }

    public void setItemsAgreed(boolean itemsAgreed) {
        this.itemsAgreed = itemsAgreed;
    }

    public boolean isNameAgreed() {
        return nameAgreed;
    }

    public void setNameAgreed(boolean nameAgreed) {
        this.nameAgreed = nameAgreed;
    }

    public List<IslandItem> getFinalItems() {
        return finalItems;
    }

    public void setFinalItems(List<IslandItem> finalItems) {
        this.finalItems = finalItems;
    }

    public String getIslandName() {
        return islandName;
    }

    public void setIslandName(String islandName) {
        this.islandName = islandName;
    }

    public IslandPairCardDTO getPairCard() {
        return pairCard;
    }

    public void setPairCard(IslandPairCardDTO pairCard) {
        this.pairCard = pairCard;
    }
}
