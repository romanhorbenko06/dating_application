package com.example.dating_application.Service;

import com.example.dating_application.Entity.IslandItem;
import com.example.dating_application.Entity.IslandNightEvent;
import com.example.dating_application.Entity.IslandPairSignature;
import com.example.dating_application.Entity.IslandPairStyle;
import com.example.dating_application.Entity.IslandRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.dating_application.Entity.IslandItem.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Ролі, стиль пари, рідкісні збіги й підпис. */
class IslandStyleRulesTest {

    private static final String KEY_A = "GUITAR,MATCHES,WATER_BARREL|острів";
    private static final String KEY_B = "FLAMINGO,GUITAR,MATCHES|острів";

    @Test
    @DisplayName("кожна роль має рівно три предмети")
    void everyRoleHasThreeItems() {
        Map<IslandRole, Long> perRole = Arrays.stream(IslandItem.values())
                .collect(Collectors.groupingBy(IslandItem::getRole, Collectors.counting()));
        assertThat(perRole).containsOnlyKeys(IslandRole.values());
        assertThat(perRole.values()).allMatch(n -> n == 3);
    }

    @Test
    @DisplayName("у кожній нічній події всі чотири ролі представлені рівно раз")
    void everyNightEventCoversAllRoles() {
        for (IslandNightEvent event : IslandNightEvent.values()) {
            EnumSet<IslandRole> roles = EnumSet.noneOf(IslandRole.class);
            for (int i = 0; i < event.getOptions().size(); i++) {
                roles.add(event.roleOf(i));
            }
            assertThat(roles).as(event.name()).containsExactlyInAnyOrder(IslandRole.values());
        }
    }

    @Test
    @DisplayName("реакції «дивного звуку» — як в описі гри")
    void strangeSoundReactionsMapToRoles() {
        assertThat(IslandNightEvent.STRANGE_SOUND.roleOf(0)).isEqualTo(IslandRole.SEEKER);  // іду перевіряти
        assertThat(IslandNightEvent.STRANGE_SOUND.roleOf(1)).isEqualTo(IslandRole.COZY);    // ховаюсь
        assertThat(IslandNightEvent.STRANGE_SOUND.roleOf(2)).isEqualTo(IslandRole.HOST);    // готую вогонь
        assertThat(IslandNightEvent.STRANGE_SOUND.roleOf(3)).isEqualTo(IslandRole.SPARK);   // це не я
    }

    @Test
    @DisplayName("роль — та, до якої належать щонайменше два предмети; ніч тоді не важить")
    void roleByMajorityOfItems() {
        assertThat(IslandStyleRules.roleOf(List.of(MATCHES, SWISS_KNIFE, GUITAR), IslandRole.SPARK))
                .isEqualTo(IslandRole.HOST);
        assertThat(IslandStyleRules.roleOf(List.of(HAMMOCK, SEEDS, BOOK), IslandRole.SEEKER))
                .isEqualTo(IslandRole.COZY);
    }

    @Test
    @DisplayName("1+1+1 — вирішує реакція на нічну подію")
    void nightDecidesWhenItemsAreSpread() {
        assertThat(IslandStyleRules.roleOf(List.of(MATCHES, FLARE_GUN, GUITAR), IslandRole.COZY))
                .isEqualTo(IslandRole.COZY);
    }

    @Test
    @DisplayName("звичайних стилів десять, по одному на кожну пару ролей, і порядок ролей не важить")
    void tenRegularStyles() {
        EnumSet<IslandPairStyle> seen = EnumSet.noneOf(IslandPairStyle.class);
        for (IslandRole a : IslandRole.values()) {
            for (IslandRole b : IslandRole.values()) {
                assertThat(IslandPairStyle.of(a, b)).isEqualTo(IslandPairStyle.of(b, a));
                seen.add(IslandPairStyle.of(a, b));
            }
        }
        assertThat(seen).hasSize(10).noneMatch(IslandPairStyle::isRare);
        assertThat(IslandPairStyle.of(IslandRole.HOST, IslandRole.SPARK)).isEqualTo(IslandPairStyle.PRACTICAL_SOULS);
    }

    @Test
    @DisplayName("без рідкісного збігу стиль іде за ролями")
    void regularStyleFromRoles() {
        assertThat(style(List.of(MATCHES, GUITAR, WATER_BARREL), List.of(GUITAR, BOOK, HAMMOCK), 1, 1,
                IslandRole.HOST, IslandRole.COZY)).isEqualTo(IslandPairStyle.ISLAND_KEEPERS);
    }

    @Test
    @DisplayName("рідкісні: однакова трійка перебиває все, навіть фламінго в обох")
    void sameTripleBeatsEverything() {
        assertThat(style(List.of(FLAMINGO, GUITAR, MATCHES), List.of(MATCHES, FLAMINGO, GUITAR), 3, 3,
                IslandRole.SPARK, IslandRole.SPARK)).isEqualTo(IslandPairStyle.SAME_TRIPLE);
    }

    @Test
    @DisplayName("рідкісні: хтось угадав 3 з 3 — «Читаєте одне одного»")
    void threeOfThreeGuess() {
        assertThat(style(List.of(FLAMINGO, GUITAR, MATCHES), List.of(BOOK, FLAMINGO, SEEDS), 0, 3,
                IslandRole.SPARK, IslandRole.COZY)).isEqualTo(IslandPairStyle.MIND_READERS);
    }

    @Test
    @DisplayName("рідкісні: фламінго, рулони, ракетниця з насінням")
    void itemCoincidences() {
        assertThat(style(List.of(FLAMINGO, GUITAR, MATCHES), List.of(FLAMINGO, BOOK, SEEDS), 1, 1,
                IslandRole.SPARK, IslandRole.COZY)).isEqualTo(IslandPairStyle.FLAMINGO_TWINS);
        assertThat(style(List.of(TOILET_PAPER, GUITAR, MATCHES), List.of(TOILET_PAPER, BOOK, SEEDS), 1, 1,
                IslandRole.SPARK, IslandRole.COZY)).isEqualTo(IslandPairStyle.TWO_WITH_ROLLS);
        assertThat(style(List.of(FLARE_GUN, GUITAR, MATCHES), List.of(HAMMOCK, BOOK, SEEDS), 0, 0,
                IslandRole.HOST, IslandRole.COZY)).isEqualTo(IslandPairStyle.FLARE_AND_SEEDS);
        assertThat(style(List.of(HAMMOCK, BOOK, SEEDS), List.of(FLARE_GUN, GUITAR, MATCHES), 0, 0,
                IslandRole.COZY, IslandRole.HOST))
                .as("байдуже, хто з двох узяв ракетницю")
                .isEqualTo(IslandPairStyle.FLARE_AND_SEEDS);
    }

    @Test
    @DisplayName("без розбіжностей: швидше 30 с — «Швидко домовились», інакше «Один веде»")
    void signatureWithoutDisagreement() {
        assertThat(IslandStyleRules.signature(false, KEY_A, KEY_A, KEY_A, Duration.ofSeconds(12)))
                .isEqualTo(IslandPairSignature.QUICK_DEAL);
        assertThat(IslandStyleRules.signature(false, KEY_A, KEY_A, KEY_A, Duration.ofHours(3)))
                .isEqualTo(IslandPairSignature.ONE_LEADS);
    }

    @Test
    @DisplayName("був торг: фінал — чийсь стартовий варіант → «Сперечаєтесь», новий спільний → «Разом»")
    void signatureWithDisagreement() {
        assertThat(IslandStyleRules.signature(true, KEY_B, KEY_A, KEY_B, Duration.ofSeconds(5)))
                .isEqualTo(IslandPairSignature.ARGUE_WITH_SMILE);
        assertThat(IslandStyleRules.signature(true, "BOOK,GUITAR,MATCHES|острів", KEY_A, KEY_B, Duration.ofSeconds(5)))
                .isEqualTo(IslandPairSignature.DECIDE_TOGETHER);
    }

    @Test
    @DisplayName("відбиток пропозиції не залежить від порядку предметів, регістру й пробілів у назві")
    void proposalKeyIsNormalized() {
        assertThat(IslandStyleRules.proposalKey(List.of(GUITAR, MATCHES), "  Сонячний   Фламінго "))
                .isEqualTo(IslandStyleRules.proposalKey(List.of(MATCHES, GUITAR), "сонячний фламінго"));
    }

    private static IslandPairStyle style(List<IslandItem> a, List<IslandItem> b, int hitsA, int hitsB,
                                         IslandRole roleA, IslandRole roleB) {
        return IslandStyleRules.pairStyle(a, b, hitsA, hitsB, roleA, roleB);
    }
}
