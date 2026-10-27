package com.example.dating_application.Config;

import com.example.dating_application.Entity.Tag;
import com.example.dating_application.Repo.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Засіває фіксований каталог тегів, якщо таблиця порожня. */
@Component
@Order(1)
public class TagInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TagInitializer.class);

    private final TagRepository tagRepository;

    public TagInitializer(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    private static final List<String> TAGS = List.of(
            // Спорт та активність
            "Футбол", "Баскетбол", "Теніс", "Біг", "Велосипед", "Плавання",
            "Спортзал і фітнес", "Йога", "Походи", "Скелелазіння", "Лижі", "Сноуборд",
            "Серфінг", "Бойові мистецтва", "Бокс", "Танці", "Скейтборд", "Гольф",
            "Волейбол", "Настільний теніс", "Риболовля", "Верхова їзда", "Каякінг",

            // Музика
            "Рок", "Поп", "Хіп-хоп", "Джаз", "Класична музика", "Електронна музика",
            "Метал", "Інді", "К-поп", "Ритм-н-блюз", "Кантрі", "Гра на гітарі",
            "Гра на фортепіано", "Спів", "Концерти та фестивалі", "Вінілові платівки",

            // Мистецтво та творчість
            "Живопис", "Малювання", "Фотографія", "Письменництво", "Поезія", "Скульптура",
            "Графічний дизайн", "Відеозйомка", "Театр", "Рукоділля", "В’язання",
            "Каліграфія", "Гончарство",

            // Кіно, книги, ігри
            "Кіно", "Серіали", "Аніме", "Читання", "Фентезі", "Наукова фантастика",
            "Комікси", "Настільні ігри", "Відеоігри", "Шахи", "Головоломки", "Настільні рольові ігри",

            // Їжа та напої
            "Кулінарія", "Випічка", "Кава", "Чай", "Вино", "Крафтове пиво", "Вегетаріанство",
            "Веганство", "Гастрономія", "Барбекю", "Суші", "Вулична їжа",

            // Подорожі та природа
            "Подорожі", "Бекпекінг", "Автоподорожі", "Кемпінг", "Пляжі", "Гори",
            "Природа", "Садівництво", "Астрономія", "Спостереження за птахами", "Шосейний велоспорт",

            // Технології та навчання
            "Програмування", "Штучний інтелект", "Ґаджети", "Стартапи",
            "Наука", "Космос", "Історія", "Філософія", "Психологія", "Іноземні мови",
            "Інвестиції", "Криптовалюти", "Робототехніка",

            // Стиль життя
            "Мода", "Макіяж", "Догляд за шкірою", "Дизайн інтер’єру", "Мінімалізм",
            "Медитація", "Волонтерство", "Домашні улюбленці", "Собаки", "Коти", "Автомобілі",
            "Мотоцикли", "Нічне життя", "Дегустація вин", "Екологічність",
            "Духовність", "Політика", "Підприємництво"
    );

    @Override
    public void run(String... args) {
        if (tagRepository.count() > 0) {
            return;
        }

        List<Tag> tags = TAGS.stream()
                .map(name -> {
                    Tag t = new Tag();
                    t.setTagName(name);
                    return t;
                })
                .collect(Collectors.toList());

        tagRepository.saveAll(tags);
        logger.info("Seeded {} interest tags", tags.size());
    }
}