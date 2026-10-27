package com.example.dating_application.DTO.Response;

import java.util.List;

public class IslandNightEventDTO {
    private String code;
    private String prompt;
    private List<String> options;

    public IslandNightEventDTO() {
    }

    public IslandNightEventDTO(String code, String prompt, List<String> options) {
        this.code = code;
        this.prompt = prompt;
        this.options = options;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
