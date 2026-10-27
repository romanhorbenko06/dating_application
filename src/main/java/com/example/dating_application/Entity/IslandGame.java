package com.example.dating_application.Entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Партія «Острова на двох» у чаті. Гра асинхронна, тож кожна фаза має лише дедлайн expiresAt.
 * Мітки часу водночас кажуть, що вже можна показувати гравцям.
 */
@Entity
@Table(name = "island_games", indexes = @Index(name = "idx_island_game_chat", columnList = "chat_id"))
public class IslandGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long gameId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invited_by_id", nullable = false)
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IslandGameStatus status;

    /** Дедлайн поточної фази; null, коли партія завершена. */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime revealedAt;
    private LocalDateTime nightRevealedAt;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private IslandNightEvent nightEvent;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "island_game_final_items", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "position")
    @Enumerated(EnumType.STRING)
    @Column(name = "item", nullable = false)
    private List<IslandItem> finalItems = new ArrayList<>();

    @Column(length = 40)
    private String islandName;

    /** Boolean, щоб ddl-auto=update додав колонку до наявних рядків. */
    private Boolean shortlistDisagreed;

    /** Фіксуються на фіналі, щоб картка не змінилась після оновлення правил. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private IslandPairStyle pairStyle;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private IslandPairSignature pairSignature;

    /** EAGER: вигляд будується після транзакції; Set, бо два EAGER-списки Hibernate не тягне разом. */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    private Set<IslandPlayer> players = new HashSet<>();

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }

    public IslandGameStatus getStatus() {
        return status;
    }

    public void setStatus(IslandGameStatus status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getRevealedAt() {
        return revealedAt;
    }

    public void setRevealedAt(LocalDateTime revealedAt) {
        this.revealedAt = revealedAt;
    }

    public LocalDateTime getNightRevealedAt() {
        return nightRevealedAt;
    }

    public void setNightRevealedAt(LocalDateTime nightRevealedAt) {
        this.nightRevealedAt = nightRevealedAt;
    }

    public IslandNightEvent getNightEvent() {
        return nightEvent;
    }

    public void setNightEvent(IslandNightEvent nightEvent) {
        this.nightEvent = nightEvent;
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

    public Set<IslandPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(Set<IslandPlayer> players) {
        this.players = players;
    }

    public boolean isShortlistDisagreed() {
        return Boolean.TRUE.equals(shortlistDisagreed);
    }

    public void setShortlistDisagreed(Boolean shortlistDisagreed) {
        this.shortlistDisagreed = shortlistDisagreed;
    }

    public IslandPairStyle getPairStyle() {
        return pairStyle;
    }

    public void setPairStyle(IslandPairStyle pairStyle) {
        this.pairStyle = pairStyle;
    }

    public IslandPairSignature getPairSignature() {
        return pairSignature;
    }

    public void setPairSignature(IslandPairSignature pairSignature) {
        this.pairSignature = pairSignature;
    }
}
