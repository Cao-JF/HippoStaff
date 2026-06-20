package net.mwtw.hippoStaff.vanish.rules;

import java.util.Arrays;
import java.util.Locale;

public enum VanishRule {
    CAN_BREAK_BLOCKS("can_break_blocks"),
    CAN_PLACE_BLOCKS("can_place_blocks"),
    CAN_INTERACT("can_interact"),
    CAN_HIT_ENTITIES("can_hit_entities"),
    CAN_PICKUP_ITEMS("can_pickup_items"),
    CAN_DROP_ITEMS("can_drop_items"),
    CAN_CHAT("can_chat"),
    CAN_TRIGGER_PHYSICAL("can_trigger_physical"),
    CAN_THROW("can_throw"),
    MOB_TARGETING("mob_targeting");

    private final String key;

    VanishRule(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public static VanishRule fromInput(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(rule -> rule.key.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
