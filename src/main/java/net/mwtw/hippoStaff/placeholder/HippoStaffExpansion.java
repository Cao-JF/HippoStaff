package net.mwtw.hippoStaff.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.vanish.VanishManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HippoStaffExpansion extends PlaceholderExpansion {
    private final VanishManager vanishManager;
    private final MessageService messageService;

    public HippoStaffExpansion(VanishManager vanishManager, MessageService messageService) {
        this.vanishManager = vanishManager;
        this.messageService = messageService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hippostaff";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HippoStaff";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        return switch (params.toLowerCase()) {
            case "isvanished" -> String.valueOf(this.vanishManager.isVanished(player.getUniqueId()));
            case "vanishprefix" -> this.vanishManager.isVanished(player.getUniqueId())
                    ? this.messageService.raw("vanish.placeholder.prefix", player)
                    : "";
            default -> null;
        };
    }
}
