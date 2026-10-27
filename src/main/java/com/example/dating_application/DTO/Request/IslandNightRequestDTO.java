package com.example.dating_application.DTO.Request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Верхню межу індексу перевіряє сервіс. */
public class IslandNightRequestDTO {

    @NotNull(message = "choice is required")
    @Min(value = 0, message = "choice must not be negative")
    private Integer choice;

    public IslandNightRequestDTO() {
    }

    public Integer getChoice() {
        return choice;
    }

    public void setChoice(Integer choice) {
        this.choice = choice;
    }
}
