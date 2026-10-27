package com.example.dating_application.Entity;

/** Каталог предметів гри. У БД зберігається назва константи, тож перейменовувати її не можна. */
public enum IslandItem {

    MATCHES("Сірники", "Практик, надійна база", IslandRole.HOST),
    SWISS_KNIFE("Швейцарський ніж", "Самостійний, контроль", IslandRole.HOST),
    WATER_BARREL("Бочка питної води", "Обережний, думає про ризики", IslandRole.HOST),
    FLARE_GUN("Ракетниця", "Хоче швидко додому", IslandRole.SEEKER),
    SEEDS("Насіння овочів", "Планує надовго", IslandRole.COZY),
    GUITAR("Гітара", "Романтик, душа компанії", IslandRole.SPARK),
    HAMMOCK("Гамак", "Вміє відпочивати", IslandRole.COZY),
    BOOK("Книжка без обкладинки", "Глибина, любов до історій", IslandRole.COZY),
    FLAMINGO("Надувний фламінго", "Гумор, легкість", IslandRole.SPARK),
    TOILET_PAPER("100 рулонів паперу", "Іронія або практичність", IslandRole.SPARK),
    ROUTER("Роутер без інтернету", "Самоіронія, зв'язок", IslandRole.SEEKER),
    MYSTERY_BOX("Скринька-сюрприз", "Цікавість, ризик", IslandRole.SEEKER);

    private final String title;
    private final String trait;
    private final IslandRole role;

    IslandItem(String title, String trait, IslandRole role) {
        this.title = title;
        this.trait = trait;
        this.role = role;
    }

    public String getTitle() {
        return title;
    }

    public String getTrait() {
        return trait;
    }

    public IslandRole getRole() {
        return role;
    }
}
