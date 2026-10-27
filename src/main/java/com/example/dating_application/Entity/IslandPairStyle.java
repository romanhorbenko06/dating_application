package com.example.dating_application.Entity;

import java.util.EnumSet;
import java.util.Set;

/** Стиль пари: десять звичайних (по парі ролей) і п'ять рідкісних, що перебивають звичайний. */
public enum IslandPairStyle {

    CAMP_ON_SCHEDULE(IslandRole.HOST, IslandRole.HOST, "Табір за розкладом",
            "У вас є вогонь, вода і графік чергувань. Острів ще не знає, що на ньому хтось так організований"),
    EXPEDITION(IslandRole.SEEKER, IslandRole.SEEKER, "Експедиція",
            "Ви вже думаєте, що за наступним пляжем. І чи ловить там роутер"),
    RESORT_TO_NOWHERE(IslandRole.COZY, IslandRole.COZY, "Курорт із видом на нікуди",
            "За годину ви зробили з острова відпустку. Ніхто нікуди не поспішає"),
    CHAOS_TEAM(IslandRole.SPARK, IslandRole.SPARK, "Хаос-команда",
            "Фламінго, гітара й туалетний папір. Виживання під питанням, вечірка гарантована"),
    TACTICIANS(IslandRole.HOST, IslandRole.SEEKER, "Тактики",
            "Один тримає базу, інший стежить за горизонтом. Такий острів не втратиш"),
    ISLAND_KEEPERS(IslandRole.HOST, IslandRole.COZY, "Господарі острова",
            "Надійно, як бункер, і затишно, як вечір у гамаку"),
    PRACTICAL_SOULS(IslandRole.HOST, IslandRole.SPARK, "Практики з душею",
            "Ніж нарізав, гітара заграла. Вижити можна й з настроєм"),
    DREAMY_TRAVELERS(IslandRole.SEEKER, IslandRole.COZY, "Мандрівники-мрійники",
            "Один дивиться вдалину, інший вже облаштувався. І обом добре"),
    ADVENTURE_HUNTERS(IslandRole.SEEKER, IslandRole.SPARK, "Мисливці за пригодами",
            "Скринька відкрита, фламінго надутий, план з'явиться пізніше"),
    BEACH_SOUL(IslandRole.COZY, IslandRole.SPARK, "Душа пляжу",
            "З вами острів схожий на місце, де хочеться залишитись ще на вечір"),

    SAME_TRIPLE(null, null, "Одна трійка на двох",
            "Ви навіть не домовлялись. Так буває"),
    MIND_READERS(null, null, "Читаєте одне одного",
            "Ви читаєте одне одного краще, ніж навігатор"),
    FLAMINGO_TWINS(null, null, "Фламінго-близнюки",
            "Це не збіг, це доля з надувним крилом"),
    TWO_WITH_ROLLS(null, null, "Двоє з рулонами",
            "Або ви найпрактичніша пара, або найсмішніша. Ми ще не вирішили"),
    FLARE_AND_SEEDS(null, null, "Ракетниця й насіння",
            "Одна людина хоче додому, іншій і тут добре. Це найцікавіша розмова, яку ви ще не почали");

    private final IslandRole first;
    private final IslandRole second;
    private final String title;
    private final String text;

    IslandPairStyle(IslandRole first, IslandRole second, String title, String text) {
        this.first = first;
        this.second = second;
        this.title = title;
        this.text = text;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public boolean isRare() {
        return first == null;
    }

    public static IslandPairStyle of(IslandRole a, IslandRole b) {
        Set<IslandRole> pair = EnumSet.of(a, b);
        for (IslandPairStyle style : values()) {
            if (!style.isRare() && EnumSet.of(style.first, style.second).equals(pair)) {
                return style;
            }
        }
        throw new IllegalStateException("No pair style for " + a + " + " + b);
    }
}
