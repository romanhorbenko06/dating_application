package com.example.dating_application.DTO.Request;

import com.example.dating_application.Entity.IslandItem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Різність предметів перевіряє сервіс. */
public class IslandPickRequestDTO {

    @NotNull(message = "items are required")
    @Size(min = 3, max = 3, message = "Pick exactly 3 items")
    private List<@NotNull(message = "item must not be null") IslandItem> items;

    @Size(max = 200, message = "Note must not exceed 200 characters")
    private String note;

    public IslandPickRequestDTO() {
    }

    public List<IslandItem> getItems() {
        return items;
    }

    public void setItems(List<IslandItem> items) {
        this.items = items;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
