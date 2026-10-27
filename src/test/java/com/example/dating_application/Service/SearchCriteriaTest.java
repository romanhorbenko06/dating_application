package com.example.dating_application.Service;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Незадане поле має лишатися null, інакше стрічка фільтруватиме зайве. */
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
