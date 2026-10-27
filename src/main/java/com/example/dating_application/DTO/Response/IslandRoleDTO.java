package com.example.dating_application.DTO.Response;

public class IslandRoleDTO {
    private String code;
    private String title;
    private String meaning;

    public IslandRoleDTO() {
    }

    public IslandRoleDTO(String code, String title, String meaning) {
        this.code = code;
        this.title = title;
        this.meaning = meaning;
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

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }
}
