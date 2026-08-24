package com.example.dating_application.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Блокування одного дейтера іншим.
 *
 * Запис односторонній (хто кого заблокував), але ефект симетричний:
 * після блокування користувачі зникають один в одного зі стрічки,
 * не можуть лайкати, писати й бачити фото. Зняти блок може лише той,
 * хто його поставив.
 *
 * Не плутати з адмін-баном — той живе прапорцем isBlocked у {@link User}
 * і є постійним.
 */
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

    /** Хто заблокував. */
    @ManyToOne
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    /** Кого заблокували. */
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