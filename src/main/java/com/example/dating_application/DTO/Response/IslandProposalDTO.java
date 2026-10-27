package com.example.dating_application.DTO.Response;

import com.example.dating_application.Entity.IslandItem;

import java.util.List;

public class IslandProposalDTO {
    private List<IslandItem> items;
    private String name;

    public IslandProposalDTO() {
    }

    public IslandProposalDTO(List<IslandItem> items, String name) {
        this.items = items;
        this.name = name;
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
