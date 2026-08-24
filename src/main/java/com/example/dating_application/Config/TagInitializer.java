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

/**
 * Засіває фіксований каталог тегів (інтересів) при старті застосунку,
 * якщо таблиця тегів порожня. Користувачі обирають теги з цього списку —
 * створювати власні не можна.
 */
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
            "Football", "Basketball", "Tennis", "Running", "Cycling", "Swimming",
            "Gym & Fitness", "Yoga", "Hiking", "Climbing", "Skiing", "Snowboarding",
            "Surfing", "Martial Arts", "Boxing", "Dancing", "Skateboarding", "Golf",
            "Volleyball", "Table Tennis", "Fishing", "Horse Riding", "Kayaking",

            // Музика
            "Rock", "Pop", "Hip-Hop", "Jazz", "Classical Music", "Electronic Music",
            "Metal", "Indie", "K-Pop", "R&B", "Country Music", "Playing Guitar",
            "Playing Piano", "Singing", "Concerts & Festivals", "Vinyl Records",

            // Мистецтво та творчість
            "Painting", "Drawing", "Photography", "Writing", "Poetry", "Sculpture",
            "Graphic Design", "Filmmaking", "Theatre", "Crafts & DIY", "Knitting",
            "Calligraphy", "Pottery",

            // Кіно, книги, ігри
            "Movies", "TV Series", "Anime", "Reading", "Fantasy Books", "Sci-Fi",
            "Comics", "Board Games", "Video Games", "Chess", "Puzzles", "Tabletop RPG",

            // Їжа та напої
            "Cooking", "Baking", "Coffee", "Tea", "Wine", "Craft Beer", "Vegetarian",
            "Vegan", "Foodie", "BBQ", "Sushi", "Street Food",

            // Подорожі та природа
            "Travel", "Backpacking", "Road Trips", "Camping", "Beaches", "Mountains",
            "Nature", "Gardening", "Astronomy", "Bird Watching", "Road Cycling",

            // Технології та навчання
            "Programming", "Artificial Intelligence", "Gadgets", "Startups",
            "Science", "Space", "History", "Philosophy", "Psychology", "Languages",
            "Investing", "Cryptocurrency", "Robotics",

            // Стиль життя
            "Fashion", "Makeup", "Skincare", "Interior Design", "Minimalism",
            "Meditation", "Volunteering", "Pets", "Dogs", "Cats", "Cars",
            "Motorcycles", "Nightlife", "Wine Tasting", "Sustainability",
            "Spirituality", "Politics", "Entrepreneurship"
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