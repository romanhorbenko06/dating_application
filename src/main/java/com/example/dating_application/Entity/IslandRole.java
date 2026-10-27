package com.example.dating_application.Entity;

public enum IslandRole {
    HOST("Господар", "Спочатку база, потім усе інше"),
    SEEKER("Шукач", "Тягнеться до невідомого й до зв'язку зі світом"),
    COZY("Затишок", "Не поспішає й вміє облаштуватись"),
    SPARK("Іскра", "Настрій і жарт важливіші за виживання");

    private final String title;
    private final String meaning;

    IslandRole(String title, String meaning) {
        this.title = title;
        this.meaning = meaning;
    }

    public String getTitle() {
        return title;
    }

    public String getMeaning() {
        return meaning;
    }
}
