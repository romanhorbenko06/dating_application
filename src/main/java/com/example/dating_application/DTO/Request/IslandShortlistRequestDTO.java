package com.example.dating_application.DTO.Request;

import com.example.dating_application.Entity.IslandItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Кількість предметів залежить від партії, тож її перевіряє сервіс. */
public class IslandShortlistRequestDTO {

    @NotNull(message = "items are required")
    @Size(min = 1, max = 6, message = "items must contain 1 to 6 entries")
    private List<@NotNull(message = "item must not be null") IslandItem> items;

    @NotBlank(message = "Island name is required")
    @Size(max = 40, message = "Island name must not exceed 40 characters")
    private String name;

    public IslandShortlistRequestDTO() {
    }

    public List<IslandItem> getItems() {
        return items;
    }

    public void setItems(List<IslandItem> items) {
        this.items = items;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
