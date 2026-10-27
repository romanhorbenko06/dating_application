package com.example.dating_application.Entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Колекції окремими SELECT-ами: чотири EAGER-списки join-ом дали б декартів добуток. */
@Entity
@Table(
        name = "island_players",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_island_player_game_user",
                columnNames = {"game_id", "user_id"}
        )
)
public class IslandPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long playerId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private IslandGame game;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "island_player_picks", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "position")
    @Enumerated(EnumType.STRING)
    @Column(name = "item", nullable = false)
    private List<IslandItem> pick = new ArrayList<>();

    @Column(length = 200)
    private String note;

    private LocalDateTime pickedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "island_player_guesses", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "position")
    @Enumerated(EnumType.STRING)
    @Column(name = "item", nullable = false)
    private List<IslandItem> guess = new ArrayList<>();

    private LocalDateTime guessedAt;

    private Integer nightChoice;

    private LocalDateTime nightAnsweredAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "island_player_proposals", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "position")
    @Enumerated(EnumType.STRING)
    @Column(name = "item", nullable = false)
    private List<IslandItem> proposalItems = new ArrayList<>();

    @Column(length = 40)
    private String proposalName;

    private LocalDateTime proposedAt;

    /** Відбиток першої пропозиції — для підпису на картці. */
    @Column(length = 400)
    private String firstProposalKey;

    public boolean hasPicked() {
        return pickedAt != null;
    }

    public boolean hasGuessed() {
        return guessedAt != null;
    }

    public boolean hasAnsweredNight() {
        return nightAnsweredAt != null;
    }

    public boolean hasProposed() {
        return proposedAt != null;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public IslandGame getGame() {
        return game;
    }

    public void setGame(IslandGame game) {
        this.game = game;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<IslandItem> getPick() {
        return pick;
    }

    public void setPick(List<IslandItem> pick) {
        this.pick = pick;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getPickedAt() {
        return pickedAt;
    }

    public void setPickedAt(LocalDateTime pickedAt) {
        this.pickedAt = pickedAt;
    }

    public List<IslandItem> getGuess() {
        return guess;
    }

    public void setGuess(List<IslandItem> guess) {
        this.guess = guess;
    }

    public LocalDateTime getGuessedAt() {
        return guessedAt;
    }

    public void setGuessedAt(LocalDateTime guessedAt) {
        this.guessedAt = guessedAt;
    }

    public Integer getNightChoice() {
        return nightChoice;
    }

    public void setNightChoice(Integer nightChoice) {
        this.nightChoice = nightChoice;
    }

    public LocalDateTime getNightAnsweredAt() {
        return nightAnsweredAt;
    }

    public void setNightAnsweredAt(LocalDateTime nightAnsweredAt) {
        this.nightAnsweredAt = nightAnsweredAt;
    }

    public List<IslandItem> getProposalItems() {
        return proposalItems;
    }

    public void setProposalItems(List<IslandItem> proposalItems) {
        this.proposalItems = proposalItems;
    }

    public String getProposalName() {
        return proposalName;
    }

    public void setProposalName(String proposalName) {
        this.proposalName = proposalName;
    }

    public LocalDateTime getProposedAt() {
        return proposedAt;
    }

    public void setProposedAt(LocalDateTime proposedAt) {
        this.proposedAt = proposedAt;
    }

    public String getFirstProposalKey() {
        return firstProposalKey;
    }

    public void setFirstProposalKey(String firstProposalKey) {
        this.firstProposalKey = firstProposalKey;
    }
}
