package com.example.dating_application.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Блокування між дейтерами: запис односторонній, ефект симетричний. Адмін-бан — окремо, прапорець isBlocked у User. */
@Entity
@Table(
        name = "block",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_block_blocker_blocked",
                columnNames = {"blocker_id", "blocked_id"}
        )
)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long blockId;

    @ManyToOne
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    private LocalDateTime createdAt;

    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    public User getBlocker() {
        return blocker;
    }

    public void setBlocker(User blocker) {
        this.blocker = blocker;
    }

    public User getBlocked() {
        return blocked;
    }

    public void setBlocked(User blocked) {
        this.blocked = blocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}