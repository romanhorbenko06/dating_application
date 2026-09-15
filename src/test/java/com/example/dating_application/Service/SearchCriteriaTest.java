package com.example.dating_application.Service;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Набір фільтрів стрічки. Простий носій даних, але з однією важливою рисою:
 * незадане поле лишається null, і саме за цим FeedService вирішує, чи додавати
 * умову в запит. Якби тут з'явилися значення за замовчуванням, стрічка почала б
 * мовчки фільтрувати те, чого користувач не просив.
 */
class SearchCriteriaTest {

    @Test
    @DisplayName("новий критерій порожній — жодного фільтра не застосується")
    void freshCriteriaIsEmpty() {
        SearchCriteria criteria = new SearchCriteria();

        assertThat(criteria.getGender()).isNull();
        assertThat(criteria.getCity()).isNull();
        assertThat(criteria.getDatingGoal()).isNull();
        assertThat(criteria.getMinAge()).isNull();
        assertThat(criteria.getMaxAge()).isNull();
    }

    @Test
    @DisplayName("задані значення повертаються як є")
    void keepsWhatItWasGiven() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setGender(Gender.MALE);
        criteria.setCity("Львів");
        criteria.setDatingGoal(DatingGoal.MARRIAGE);
        criteria.setMinAge(25);
        criteria.setMaxAge(40);

        assertThat(criteria.getGender()).isEqualTo(Gender.MALE);
        assertThat(criteria.getCity()).isEqualTo("Львів");
        assertThat(criteria.getDatingGoal()).isEqualTo(DatingGoal.MARRIAGE);
        assertThat(criteria.getMinAge()).isEqualTo(25);
        assertThat(criteria.getMaxAge()).isEqualTo(40);
    }
}
