package com.example.dating_application.DTO.Request;

import com.example.dating_application.Entity.IslandItem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class IslandGuessRequestDTO {

    @NotNull(message = "items are required")
    @Size(min = 3, max = 3, message = "Guess exactly 3 items")
    private List<@NotNull(message = "item must not be null") IslandItem> items;

    public IslandGuessRequestDTO() {
    }

    public List<IslandItem> getItems() {
        return items;
    }

    public void setItems(List<IslandItem> items) {
        this.items = items;
    }
}
