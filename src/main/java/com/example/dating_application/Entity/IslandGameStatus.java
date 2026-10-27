package com.example.dating_application.Entity;

/**
 * INVITED → PICKING → NIGHT → SHORTLIST → COMPLETED; DECLINED і EXPIRED чат не зачіпають.
 * Нове значення потребує ручного оновлення CHECK у наявній базі (див. db/manual).
 */
public enum IslandGameStatus {
    INVITED,
    PICKING,
    NIGHT,
    SHORTLIST,
    COMPLETED,
    DECLINED,
    EXPIRED;

    public boolean isActive() {
        return this == INVITED || this == PICKING || this == NIGHT || this == SHORTLIST;
    }
}
