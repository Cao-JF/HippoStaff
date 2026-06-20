package net.mwtw.hippoStaff.grant.gui;

import net.mwtw.hippoStaff.grant.GrantAction;
import net.mwtw.hippoStaff.grant.GrantableGroup;
import net.mwtw.hippoStaff.grant.GrantManager;
import net.mwtw.hippoStaff.grant.GrantRecord;
import net.mwtw.hippoStaff.message.MessageService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GrantGuiService {
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final GrantManager grantManager;
    private final MessageService messageService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public GrantGuiService(Plugin plugin, GrantManager grantManager, MessageService messageService) {
        this.plugin = plugin;
        this.grantManager = grantManager;
        this.messageService = messageService;
    }

    public void openMainMenu(Player staff, UUID targetUuid, String targetName) {
        Map<String, String> vars = Map.of("player", targetName);
        Inventory inventory = Bukkit.createInventory(new GrantHolder(Screen.MAIN, targetUuid, targetName, null), 27, legacy(this.messageService.raw("grant.gui.title.main", staff, vars)));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, " ");
        inventory.setItem(11, item(Material.EMERALD, this.messageService.raw("grant.gui.main.grant.name", staff, vars), this.messageService.rawList("grant.gui.main.grant.lore", staff, vars)));
        inventory.setItem(13, item(Material.BARRIER, this.messageService.raw("grant.gui.main.revoke.name", staff, vars), this.messageService.rawList("grant.gui.main.revoke.lore", staff, vars)));
        inventory.setItem(15, item(Material.BOOK, this.messageService.raw("grant.gui.main.history.name", staff, vars), this.messageService.rawList("grant.gui.main.history.lore", staff, vars)));
        inventory.setItem(22, item(Material.CHEST, this.messageService.raw("grant.gui.main.active.name", staff, vars), this.messageService.rawList("grant.gui.main.active.lore", staff, vars)));
        staff.openInventory(inventory);
    }

    public void openGrantGroups(Player staff, UUID targetUuid, String targetName) {
        List<GrantableGroup> groups = this.grantManager.getGrantableGroupEntries();
        Map<String, String> vars = Map.of("player", targetName);
        Inventory inventory = Bukkit.createInventory(new GrantHolder(Screen.GROUP_SELECT, targetUuid, targetName, null), 54, legacy(this.messageService.raw("grant.gui.title.group-select", staff, vars)));
        fillBottomBar(inventory);
        for (int i = 0; i < Math.min(groups.size(), 45); i++) {
            GrantableGroup group = groups.get(i);
            String display = group.prefix();
            Map<String, String> lineVars = Map.of("player", targetName, "group", group.name(), "group_display", display);
            inventory.setItem(i, item(Material.NAME_TAG, this.messageService.raw("grant.gui.group-item.name", staff, lineVars), this.messageService.rawList("grant.gui.group-item.lore", staff, lineVars)));
        }
        inventory.setItem(49, backItem(staff, vars));
        staff.openInventory(inventory);
    }

    public void openDurationSelect(Player staff, UUID targetUuid, String targetName, String group) {
        Map<String, String> vars = Map.of("player", targetName, "group", group);
        Inventory inventory = Bukkit.createInventory(new GrantHolder(Screen.DURATION_SELECT, targetUuid, targetName, group), 27, legacy(this.messageService.raw("grant.gui.title.duration-select", staff, vars)));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, " ");
        inventory.setItem(10, durationItem(staff, "1h", vars));
        inventory.setItem(11, durationItem(staff, "12h", vars));
        inventory.setItem(12, durationItem(staff, "1d", vars));
        inventory.setItem(13, durationItem(staff, "7d", vars));
        inventory.setItem(14, durationItem(staff, "30d", vars));
        inventory.setItem(16, durationItem(staff, "perm", vars));
        inventory.setItem(22, item(Material.ARROW, this.messageService.raw("grant.gui.back-groups.name", staff, vars), this.messageService.rawList("grant.gui.back-groups.lore", staff, vars)));
        staff.openInventory(inventory);
    }

    public void openRevokeMenu(Player staff, UUID targetUuid, String targetName) {
        List<GrantRecord> active = this.grantManager.active(targetUuid);
        Map<String, String> vars = Map.of("player", targetName);
        Inventory inventory = Bukkit.createInventory(new GrantHolder(Screen.REVOKE_SELECT, targetUuid, targetName, null), 54, legacy(this.messageService.raw("grant.gui.title.revoke-select", staff, vars)));
        fillBottomBar(inventory);
        for (int i = 0; i < Math.min(active.size(), 45); i++) {
            GrantRecord record = active.get(i);
            String remaining = "permanent";
            if (record.expiresAtEpochMillis() != null) {
                Duration left = Duration.ofMillis(Math.max(0L, record.expiresAtEpochMillis() - Instant.now().toEpochMilli()));
                remaining = this.grantManager.formatDuration(left);
            }
            Map<String, String> lineVars = Map.of("player", targetName, "group", record.group(), "remaining", remaining, "actor", record.grantedBy());
            inventory.setItem(i, item(Material.RED_DYE, this.messageService.raw("grant.gui.revoke-item.name", staff, lineVars), this.messageService.rawList("grant.gui.revoke-item.lore", staff, lineVars)));
        }
        inventory.setItem(49, backItem(staff, vars));
        staff.openInventory(inventory);
    }

    public void openActiveList(Player staff, UUID targetUuid, String targetName) {
        List<GrantRecord> active = this.grantManager.active(targetUuid);
        Map<String, String> vars = Map.of("player", targetName);
        Inventory inventory = Bukkit.createInventory(new GrantHolder(Screen.ACTIVE_LIST, targetUuid, targetName, null), 54, legacy(this.messageService.raw("grant.gui.title.active-list", staff, vars)));
        fillBottomBar(inventory);
        for (int i = 0; i < Math.min(active.size(), 45); i++) {
            GrantRecord record = active.get(i);
            String remaining = "permanent";
            if (record.expiresAtEpochMillis() != null) {
                Duration left = Duration.ofMillis(Math.max(0L, record.expiresAtEpochMillis() - Instant.now().toEpochMilli()));
                remaining = this.grantManager.formatDuration(left);
            }
            Map<String, String> lineVars = Map.of("player", targetName, "group", record.group(), "remaining", remaining, "actor", record.grantedBy());
            inventory.setItem(i, item(Material.LIME_DYE, this.messageService.raw("grant.gui.active-item.name", staff, lineVars), this.messageService.rawList("grant.gui.active-item.lore", staff, lineVars)));
        }
        inventory.setItem(49, backItem(staff, vars));
        staff.openInventory(inventory);
    }

    public void openHistory(Player staff, UUID targetUuid, String targetName) {
        List<GrantRecord> history = this.grantManager.history(targetUuid);
        Map<String, String> vars = Map.of("player", targetName);
        Inventory inventory = Bukkit.createInventory(new GrantHolder(Screen.HISTORY, targetUuid, targetName, null), 54, legacy(this.messageService.raw("grant.gui.title.history", staff, vars)));
        fillBottomBar(inventory);
        for (int i = 0; i < Math.min(history.size(), 45); i++) {
            GrantRecord record = history.get(i);
            Material icon = switch (record.action()) {
                case GRANTED -> Material.GREEN_CANDLE;
                case REVOKED -> Material.RED_CANDLE;
                case EXPIRED -> Material.GRAY_CANDLE;
            };
            Map<String, String> lineVars = Map.of(
                    "player", targetName,
                    "group", record.group(),
                    "actor", record.grantedBy(),
                    "action", record.action().name(),
                    "time", HISTORY_TIME.format(Instant.ofEpochMilli(record.grantedAtEpochMillis()))
            );
            inventory.setItem(i, item(icon, this.messageService.raw("grant.gui.history-item.name", staff, lineVars), this.messageService.rawList("grant.gui.history-item.lore", staff, lineVars)));
        }
        inventory.setItem(49, backItem(staff, vars));
        staff.openInventory(inventory);
    }

    public void handleMainClick(Player staff, GrantHolder holder, int slot) {
        if (slot == 11) {
            openGrantGroups(staff, holder.targetUuid(), holder.targetName());
            return;
        }
        if (slot == 13) {
            openRevokeMenu(staff, holder.targetUuid(), holder.targetName());
            return;
        }
        if (slot == 15) {
            openHistory(staff, holder.targetUuid(), holder.targetName());
            return;
        }
        if (slot == 22) {
            openActiveList(staff, holder.targetUuid(), holder.targetName());
        }
    }

    public void handleGroupClick(Player staff, GrantHolder holder, int slot, ItemStack clicked) {
        if (slot == 49) {
            openMainMenu(staff, holder.targetUuid(), holder.targetName());
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getItemMeta() == null) {
            return;
        }
        List<GrantableGroup> groups = this.grantManager.getGrantableGroupEntries();
        if (slot < 0 || slot >= groups.size() || slot >= 45) {
            return;
        }
        String group = groups.get(slot).name();
        openDurationSelect(staff, holder.targetUuid(), holder.targetName(), group);
    }

    public void handleDurationClick(Player staff, GrantHolder holder, int slot, ItemStack clicked) {
        if (slot == 22) {
            openGrantGroups(staff, holder.targetUuid(), holder.targetName());
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getItemMeta() == null) {
            return;
        }
        String durationRaw = clicked.getItemMeta().getDisplayName();
        Duration duration = "perm".equalsIgnoreCase(durationRaw) ? null : net.mwtw.hippoStaff.grant.DurationParser.parse(durationRaw);
        this.grantManager.grant(holder.targetName(), holder.targetUuid(), holder.selectedGroup(), staff.getName(), duration, "GUI grant")
                .thenRun(() -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                    this.messageService.send(staff, "grant.success", null, Map.of(
                            "player", holder.targetName(),
                            "group", holder.selectedGroup(),
                            "duration", duration == null ? "permanent" : this.grantManager.formatDuration(duration)
                    ));
                    staff.closeInventory();
                }))
                .exceptionally(exception -> {
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(staff, "grant.player-not-found"));
                    return null;
                });
    }

    public void handleRevokeClick(Player staff, GrantHolder holder, int slot, ItemStack clicked) {
        if (slot == 49) {
            openMainMenu(staff, holder.targetUuid(), holder.targetName());
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getItemMeta() == null) {
            return;
        }
        List<GrantRecord> active = this.grantManager.active(holder.targetUuid());
        if (slot < 0 || slot >= active.size() || slot >= 45) {
            return;
        }
        String group = active.get(slot).group();
        this.grantManager.revoke(holder.targetName(), holder.targetUuid(), group, staff.getName())
                .thenRun(() -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                    this.messageService.send(staff, "grant.revoked", null, Map.of("player", holder.targetName(), "group", group));
                    openRevokeMenu(staff, holder.targetUuid(), holder.targetName());
                }))
                .exceptionally(exception -> {
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(staff, "grant.player-not-found"));
                    return null;
                });
    }

    public void handleBackOnly(Player staff, GrantHolder holder, int slot) {
        if (slot == 49) {
            openMainMenu(staff, holder.targetUuid(), holder.targetName());
        }
    }

    private ItemStack durationItem(Player viewer, String text, Map<String, String> vars) {
        Map<String, String> lineVars = new java.util.HashMap<>(vars);
        lineVars.put("duration", text);
        return item(Material.CLOCK, this.messageService.raw("grant.gui.duration-item.name", viewer, lineVars), this.messageService.rawList("grant.gui.duration-item.lore", viewer, lineVars));
    }

    private ItemStack backItem(Player viewer, Map<String, String> vars) {
        return item(Material.ARROW, this.messageService.raw("grant.gui.back.name", viewer, vars), this.messageService.rawList("grant.gui.back.lore", viewer, vars));
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemName(legacy(name));
        meta.setLore(lore.stream().map(this::legacy).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private String legacy(String mini) {
        return this.legacySerializer.serialize(this.miniMessage.deserialize(legacyToMini(mini)));
    }

    private String legacyToMini(String input) {
        StringBuilder out = new StringBuilder(input.length() + 32);
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String tag = legacyCodeToMiniTag(code);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
                if (code == 'x' && i + 13 < input.length()) {
                    String hex = parseLegacyHex(input, i + 2);
                    if (hex != null) {
                        out.append("<#").append(hex).append(">");
                        i += 13;
                        continue;
                    }
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private String legacyCodeToMiniTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private String parseLegacyHex(String input, int startIndex) {
        StringBuilder hex = new StringBuilder(6);
        int index = startIndex;
        for (int n = 0; n < 6; n++) {
            if (index + 1 >= input.length() || input.charAt(index) != '&') {
                return null;
            }
            char nibble = input.charAt(index + 1);
            if (!isHex(nibble)) {
                return null;
            }
            hex.append(nibble);
            index += 2;
        }
        return hex.toString();
    }

    private boolean isHex(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f');
    }

    private void fill(Inventory inventory, Material material, String name) {
        ItemStack filler = item(material, name, List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private void fillBottomBar(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i <= 53; i++) {
            inventory.setItem(i, filler);
        }
    }

    public enum Screen {
        MAIN,
        GROUP_SELECT,
        DURATION_SELECT,
        REVOKE_SELECT,
        ACTIVE_LIST,
        HISTORY
    }

    public record GrantHolder(Screen screen, UUID targetUuid, String targetName, String selectedGroup) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
