package com.example.dating_application.Entity;

import java.util.List;

/** Події ночі. Відповідь зберігається індексом, тож порядок варіантів у наявних подіях міняти не можна. */
public enum IslandNightEvent {

    STRANGE_SOUND(
            "Вночі ви чуєте дивний звук з джунглів",
            List.of(
                    new Option("Іду перевіряти", IslandRole.SEEKER),
                    new Option("Ховаюсь і чекаю до ранку", IslandRole.COZY),
                    new Option("Кричу «Хто там?» і готую вогонь", IslandRole.HOST),
                    new Option("Кажу, що це точно не я, і роблю вигляд, що сплю", IslandRole.SPARK)
            )),
    FOREIGN_BACKPACK(
            "На березі знайшли чужий рюкзак",
            List.of(
                    new Option("Відкриваю одразу", IslandRole.SEEKER),
                    new Option("Спершу шукаю власника", IslandRole.HOST),
                    new Option("Приміряю все, що в ньому знайдеться", IslandRole.SPARK),
                    new Option("Пропоную відкрити разом на світанку", IslandRole.COZY)
            )),
    MISSING_ITEM(
            "Зранку зник один предмет",
            List.of(
                    new Option("Шукаю по всьому острову", IslandRole.SEEKER),
                    new Option("Підозрюю мавп і будую пастку", IslandRole.HOST),
                    new Option("Махаю рукою — обійдемось", IslandRole.COZY),
                    new Option("Жартую, що це підступи партнера", IslandRole.SPARK)
            ));

    public record Option(String text, IslandRole role) {
    }

    private final String prompt;
    private final List<Option> options;

    IslandNightEvent(String prompt, List<Option> options) {
        this.prompt = prompt;
        this.options = options;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getOptions() {
        return options.stream().map(Option::text).toList();
    }

    public IslandRole roleOf(int choice) {
        return options.get(choice).role();
    }
}
