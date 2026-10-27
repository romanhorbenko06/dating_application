package com.example.dating_application.Entity;

public enum IslandPairSignature {
    QUICK_DEAL("Швидко домовились"),
    ONE_LEADS("Один веде, інший підхоплює"),
    DECIDE_TOGETHER("Рішення ухвалюєте разом"),
    ARGUE_WITH_SMILE("Сперечаєтесь, але з усмішкою");

    private final String title;

    IslandPairSignature(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
