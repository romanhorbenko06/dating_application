package com.example.dating_application.Security.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    public String email;

    // На вході в логін довжину НЕ перевіряємо: вимоги до пароля можуть змінитися,
    // а старі акаунти мають лишатися робочими. Достатньо, щоб поле було заповнене.
    @NotBlank(message = "Password is required")
    public String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
