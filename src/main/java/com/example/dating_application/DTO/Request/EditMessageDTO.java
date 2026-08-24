package com.example.dating_application.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Новий текст повідомлення (FR-17.3). Чат і автор беруться з messageId та токена. */
public class EditMessageDTO {

    @NotBlank(message = "Message content is required")
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String content;

    public EditMessageDTO() {
    }

    public EditMessageDTO(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}