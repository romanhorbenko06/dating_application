package com.example.dating_application.Service;

import com.example.dating_application.Entity.IslandItem;
import com.example.dating_application.Entity.IslandPairSignature;
import com.example.dating_application.Entity.IslandPairStyle;
import com.example.dating_application.Entity.IslandRole;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Правила картки «Наш острів»: роль гравця, стиль пари й підпис. Чисті функції без Spring і БД. */
public final class IslandStyleRules {

    public static final Duration QUICK_DEAL = Duration.ofSeconds(30);

    private IslandStyleRules() {
    }

    /** Роль, до якої належать щонайменше два предмети; при 1+1+1 вирішує нічна реакція. */
    public static IslandRole roleOf(List<IslandItem> pick, IslandRole nightRole) {
        Map<IslandRole, Integer> counts = new EnumMap<>(IslandRole.class);
        pick.forEach(item -> counts.merge(item.getRole(), 1, Integer::sum));
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(nightRole);
    }

    /** Рідкісний збіг перебиває звичайний стиль. */
    public static IslandPairStyle pairStyle(List<IslandItem> pickA, List<IslandItem> pickB,
                                            int guessHitsA, int guessHitsB,
                                            IslandRole roleA, IslandRole roleB) {
        if (new HashSet<>(pickA).equals(new HashSet<>(pickB))) {
            return IslandPairStyle.SAME_TRIPLE;
        }
        if (guessHitsA == 3 || guessHitsB == 3) {
            return IslandPairStyle.MIND_READERS;
        }
        if (pickA.contains(IslandItem.FLAMINGO) && pickB.contains(IslandItem.FLAMINGO)) {
            return IslandPairStyle.FLAMINGO_TWINS;
        }
        if (pickA.contains(IslandItem.TOILET_PAPER) && pickB.contains(IslandItem.TOILET_PAPER)) {
            return IslandPairStyle.TWO_WITH_ROLLS;
        }
        if ((pickA.contains(IslandItem.FLARE_GUN) && pickB.contains(IslandItem.SEEDS))
                || (pickA.contains(IslandItem.SEEDS) && pickB.contains(IslandItem.FLARE_GUN))) {
            return IslandPairStyle.FLARE_AND_SEEDS;
        }
        return IslandPairStyle.of(roleA, roleB);
    }

    /** «Швидко» рахується лише без розбіжностей: гра асинхронна, і таймер міряв би відсутність гравця. */
    public static IslandPairSignature signature(boolean disagreed, String finalKey,
                                                String firstKeyA, String firstKeyB, Duration took) {
        if (!disagreed) {
            return took.compareTo(QUICK_DEAL) < 0 ? IslandPairSignature.QUICK_DEAL : IslandPairSignature.ONE_LEADS;
        }
        boolean compromise = !finalKey.equals(firstKeyA) && !finalKey.equals(firstKeyB);
        return compromise ? IslandPairSignature.DECIDE_TOGETHER : IslandPairSignature.ARGUE_WITH_SMILE;
    }

    /** Предмети без порядку + назва без регістру й зайвих пробілів. */
    public static String proposalKey(Collection<IslandItem> items, String name) {
        String codes = items.stream().sorted().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
        String cleanName = name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        return codes + "|" + cleanName;
    }
}
