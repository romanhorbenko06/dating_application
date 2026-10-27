package com.example.dating_application.DTO.Response;

public class IslandItemDTO {
    private String code;
    private String title;
    private String trait;

    public IslandItemDTO() {
    }

    public IslandItemDTO(String code, String title, String trait) {
        this.code = code;
        this.title = title;
        this.trait = trait;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTrait() {
        return trait;
    }

    public void setTrait(String trait) {
        this.trait = trait;
    }
}
