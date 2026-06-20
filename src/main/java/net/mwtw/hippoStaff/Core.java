package net.mwtw.hippoStaff;

import net.mwtw.hippoStaff.command.GameModeCommand;
import net.mwtw.hippoStaff.command.GrantCommand;
import net.mwtw.hippoStaff.command.GrantHistoryCommand;
import net.mwtw.hippoStaff.command.GrantListCommand;
import net.mwtw.hippoStaff.command.FixedTimeCommand;
import net.mwtw.hippoStaff.command.FeedCommand;
import net.mwtw.hippoStaff.command.FlyCommand;
import net.mwtw.hippoStaff.command.HippoStaffCommand;
import net.mwtw.hippoStaff.command.MessageCommand;
import net.mwtw.hippoStaff.command.ConfigTextCommand;
import net.mwtw.hippoStaff.command.RepairCommand;
import net.mwtw.hippoStaff.command.ReplyCommand;
import net.mwtw.hippoStaff.command.RevokeCommand;
import net.mwtw.hippoStaff.command.SpeedCommand;
import net.mwtw.hippoStaff.command.StaffChatCommand;
import net.mwtw.hippoStaff.command.VanishCommand;
import net.mwtw.hippoStaff.command.VanishListCommand;
import net.mwtw.hippoStaff.command.VanishPickupCommand;
import net.mwtw.hippoStaff.hook.HookManager;
import net.mwtw.hippoStaff.hook.VoiceChatSupport;
import net.mwtw.hippoStaff.grant.GrantManager;
import net.mwtw.hippoStaff.grant.gui.GrantGuiService;
import net.mwtw.hippoStaff.grant.MariaDbGrantStorage;
import net.mwtw.hippoStaff.grant.YamlGrantStorage;
import net.mwtw.hippoStaff.listener.GrantGuiListener;
import net.mwtw.hippoStaff.listener.PlayerConnectionListener;
import net.mwtw.hippoStaff.listener.PrivateMessagePresenceListener;
import net.mwtw.hippoStaff.listener.ServerListVisibilityListener;
import net.mwtw.hippoStaff.listener.StaffChatListener;
import net.mwtw.hippoStaff.listener.VanishGhostListener;
import net.mwtw.hippoStaff.listener.VanishRuleListener;
import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.message.NetworkPlayerRegistry;
import net.mwtw.hippoStaff.message.PrivateMessageManager;
import net.mwtw.hippoStaff.message.PrivateMessageSyncService;
import net.mwtw.hippoStaff.placeholder.HippoStaffExpansion;
import net.mwtw.hippoStaff.staffchat.StaffChatManager;
import net.mwtw.hippoStaff.staffchat.StaffChatSyncService;
import net.mwtw.hippoStaff.storage.MariaDbVanishStorage;
import net.mwtw.hippoStaff.storage.VanishStorage;
import net.mwtw.hippoStaff.storage.YamlVanishStorage;
import net.mwtw.hippoStaff.sync.RedisSyncService;
import net.mwtw.hippoStaff.vanish.VanishManager;
import net.mwtw.hippoStaff.vanish.PickupSettingsManager;
import net.mwtw.hippoStaff.vanish.rules.RuleManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Core extends JavaPlugin {
    private VanishStorage vanishStorage;
    private VanishManager vanishManager;
    private MessageService messageService;
    private HookManager hookManager;
    private RedisSyncService redisSyncService;
    private RuleManager ruleManager;
    private VoiceChatSupport voiceChatSupport;
    private PickupSettingsManager pickupSettingsManager;
    private StaffChatManager staffChatManager;
    private StaffChatSyncService staffChatSyncService;
    private NetworkPlayerRegistry networkPlayerRegistry;
    private PrivateMessageManager privateMessageManager;
    private PrivateMessageSyncService privateMessageSyncService;
    private GrantManager grantManager;
    private GrantGuiService grantGuiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessages();

        this.messageService = new MessageService(this);
        this.vanishStorage = createStorage();
        this.ruleManager = new RuleManager(this);
        this.pickupSettingsManager = new PickupSettingsManager(this);
        this.staffChatManager = new StaffChatManager(this, this.messageService);
        this.networkPlayerRegistry = new NetworkPlayerRegistry();
        this.privateMessageManager = new PrivateMessageManager(this, this.messageService, this.networkPlayerRegistry);
        this.grantManager = new GrantManager(this, this.messageService, createGrantStorage());
        this.grantGuiService = new GrantGuiService(this, this.grantManager, this.messageService);

        try {
            this.vanishStorage.init();
            this.ruleManager.init();
            this.pickupSettingsManager.init();
            this.grantManager.init();
        } catch (Exception exception) {
            getLogger().severe("Failed to initialize storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.vanishManager = new VanishManager(this, this.vanishStorage, this.messageService);
        this.hookManager = new HookManager(this);
        this.hookManager.initialize();
        initializeVoiceChatSupport();
        initializeRedisSync();
        initializeStaffChatSync();
        initializePrivateMessageSync();
        this.staffChatManager.initDiscord();

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, this.vanishManager, this.ruleManager), this);
        getServer().getPluginManager().registerEvents(new VanishRuleListener(this.vanishManager, this.ruleManager, this.pickupSettingsManager, this.messageService), this);
        getServer().getPluginManager().registerEvents(new VanishGhostListener(this.vanishManager), this);
        getServer().getPluginManager().registerEvents(new ServerListVisibilityListener(this.vanishManager), this);
        getServer().getPluginManager().registerEvents(new StaffChatListener(this.staffChatManager), this);
        getServer().getPluginManager().registerEvents(new PrivateMessagePresenceListener(this.privateMessageManager), this);
        getServer().getPluginManager().registerEvents(new GrantGuiListener(this.grantGuiService), this);

        VanishCommand vanishCommand = new VanishCommand(this.vanishManager, this.messageService);
        registerCommand("vanish", vanishCommand, vanishCommand);
        VanishListCommand vanishListCommand = new VanishListCommand(this.vanishManager, this.messageService);
        registerCommand("vanishlist", vanishListCommand, null);
        VanishPickupCommand vanishPickupCommand = new VanishPickupCommand(this.pickupSettingsManager, this.messageService);
        registerCommand("vanishpickup", vanishPickupCommand, vanishPickupCommand);
        GameModeCommand gmCommand = new GameModeCommand(null);
        GameModeCommand gmcCommand = new GameModeCommand(GameMode.CREATIVE);
        GameModeCommand gmaCommand = new GameModeCommand(GameMode.ADVENTURE);
        GameModeCommand gmsCommand = new GameModeCommand(GameMode.SURVIVAL);
        GameModeCommand gmspCommand = new GameModeCommand(GameMode.SPECTATOR);
        registerCommand("gm", gmCommand, gmCommand);
        registerCommand("gmc", gmcCommand, gmcCommand);
        registerCommand("gma", gmaCommand, gmaCommand);
        registerCommand("gms", gmsCommand, gmsCommand);
        registerCommand("gmsp", gmspCommand, gmspCommand);
        registerCommand("gm0", gmsCommand, gmsCommand);
        registerCommand("gm1", gmcCommand, gmcCommand);
        registerCommand("gm2", gmaCommand, gmaCommand);
        registerCommand("gm3", gmspCommand, gmspCommand);
        registerCommand("day", new FixedTimeCommand(1000L), null);
        registerCommand("noon", new FixedTimeCommand(6000L), null);
        registerCommand("night", new FixedTimeCommand(13000L), null);
        registerCommand("midnight", new FixedTimeCommand(18000L), null);
        SpeedCommand speedCommand = new SpeedCommand(null);
        SpeedCommand flySpeedCommand = new SpeedCommand(SpeedCommand.SpeedType.FLY);
        SpeedCommand walkSpeedCommand = new SpeedCommand(SpeedCommand.SpeedType.WALK);
        registerCommand("speed", speedCommand, speedCommand);
        registerCommand("flyspeed", flySpeedCommand, flySpeedCommand);
        registerCommand("walkspeed", walkSpeedCommand, walkSpeedCommand);
        RepairCommand repairCommand = new RepairCommand();
        registerCommand("repair", repairCommand, repairCommand);
        FeedCommand feedCommand = new FeedCommand();
        registerCommand("feed", feedCommand, feedCommand);
        FlyCommand flyCommand = new FlyCommand(this.messageService);
        registerCommand("fly", flyCommand, flyCommand);
        MessageCommand messageCommand = new MessageCommand(this.privateMessageManager, this.messageService);
        registerCommand("msg", messageCommand, messageCommand);
        ReplyCommand replyCommand = new ReplyCommand(this.privateMessageManager, this.messageService);
        registerCommand("r", replyCommand, null);
        StaffChatCommand staffChatCommand = new StaffChatCommand(this.staffChatManager, this.messageService);
        registerCommand("staffchat", staffChatCommand, null);
        registerCommand("discord", new ConfigTextCommand(this, this.messageService, "links.discord"), null);
        registerCommand("website", new ConfigTextCommand(this, this.messageService, "links.website"), null);
        registerCommand("rules", new ConfigTextCommand(this, this.messageService, "links.rules"), null);
        HippoStaffCommand hippoStaffCommand = new HippoStaffCommand(this, this.messageService);
        registerCommand("hippostaff", hippoStaffCommand, hippoStaffCommand);
        GrantCommand grantCommand = new GrantCommand(this, this.grantManager, this.grantGuiService, this.messageService);
        registerCommand("grant", grantCommand, grantCommand);
        registerCommand("revoke", new RevokeCommand(this, this.grantManager, this.grantGuiService, this.messageService), null);
        registerCommand("granthistory", new GrantHistoryCommand(this, this.grantManager, this.messageService), null);
        registerCommand("grantlist", new GrantListCommand(this, this.grantManager, this.messageService), null);

        if (this.hookManager.isPlaceholderApiAvailable()) {
            new HippoStaffExpansion(this.vanishManager, this.messageService).register();
        }

        this.vanishManager.start();
    }

    private void saveDefaultMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    @Override
    public void onDisable() {
        if (this.staffChatManager != null) {
            this.staffChatManager.shutdownDiscord();
        }
        if (this.vanishManager != null) {
            this.vanishManager.shutdown();
        }
        if (this.redisSyncService != null) {
            this.redisSyncService.close();
        }
        if (this.staffChatSyncService != null) {
            this.staffChatSyncService.close();
        }
        if (this.privateMessageSyncService != null) {
            this.privateMessageSyncService.close();
        }
        if (this.vanishStorage != null) {
            this.vanishStorage.close();
        }
        if (this.ruleManager != null) {
            this.ruleManager.close();
        }
        if (this.pickupSettingsManager != null) {
            this.pickupSettingsManager.close();
        }
        if (this.grantManager != null) {
            this.grantManager.close();
        }
    }

    private VanishStorage createStorage() {
        String storageType = getConfig().getString("storage.type", "YAML");
        if ("MARIADB".equalsIgnoreCase(storageType)) {
            return new MariaDbVanishStorage(this);
        }
        return new YamlVanishStorage(this);
    }

    private net.mwtw.hippoStaff.grant.GrantStorage createGrantStorage() {
        String storageType = getConfig().getString("storage.type", "YAML");
        if ("MARIADB".equalsIgnoreCase(storageType)) {
            return new MariaDbGrantStorage(this);
        }
        return new YamlGrantStorage(this);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is not defined in plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void initializeRedisSync() {
        this.vanishManager.setRedisSyncService(null);
        if (this.redisSyncService != null) {
            this.redisSyncService.close();
            this.redisSyncService = null;
        }
        if (!getConfig().getBoolean("storage.redis.enabled", false)) {
            return;
        }
        try {
            this.redisSyncService = new RedisSyncService(this, (uuid, vanished) ->
                    getServer().getScheduler().runTask(this, () -> this.vanishManager.applyExternalState(uuid, vanished)));
            this.redisSyncService.init();
            this.vanishManager.setRedisSyncService(this.redisSyncService);
            getLogger().info("Redis sync enabled.");
        } catch (Exception exception) {
            getLogger().warning("Failed to initialize Redis sync: " + exception.getMessage());
            this.redisSyncService = null;
        }
    }

    private void initializeVoiceChatSupport() {
        this.vanishManager.setVoiceChatSupport(null);
        if (this.voiceChatSupport != null) {
            this.voiceChatSupport.shutdown();
            this.voiceChatSupport = null;
        }
        if (!this.hookManager.isSimpleVoiceChatAvailable()) {
            return;
        }
        if (!VoiceChatSupport.registerApiHook(this)) {
            getLogger().warning("Simple Voice Chat service was not available; API hook could not be registered.");
        }
        this.voiceChatSupport = new VoiceChatSupport(this);
        this.vanishManager.setVoiceChatSupport(this.voiceChatSupport);
    }

    private void initializeStaffChatSync() {
        this.staffChatManager.setSyncService(null);
        if (this.staffChatSyncService != null) {
            this.staffChatSyncService.close();
            this.staffChatSyncService = null;
        }
        if (!getConfig().getBoolean("staff-chat.sync.enabled", true)) {
            return;
        }
        try {
            this.staffChatSyncService = new StaffChatSyncService(this, this.staffChatManager);
            this.staffChatSyncService.init();
            this.staffChatManager.setSyncService(this.staffChatSyncService);
            getLogger().info("Staff chat Redis sync enabled.");
        } catch (Exception exception) {
            getLogger().warning("Failed to initialize staff chat Redis sync: " + exception.getMessage());
            this.staffChatSyncService = null;
        }
    }

    private void initializePrivateMessageSync() {
        this.privateMessageManager.setSyncService(null);
        if (this.privateMessageSyncService != null) {
            this.privateMessageSyncService.close();
            this.privateMessageSyncService = null;
        }
        if (!getConfig().getBoolean("private-message.sync.enabled", true)) {
            this.privateMessageManager.seedOnlinePlayers();
            return;
        }
        try {
            this.privateMessageSyncService = new PrivateMessageSyncService(this, this.networkPlayerRegistry, this.privateMessageManager);
            this.privateMessageSyncService.init();
            this.privateMessageManager.setSyncService(this.privateMessageSyncService);
            this.privateMessageManager.seedOnlinePlayers();
            getLogger().info("Private message Redis sync enabled.");
        } catch (Exception exception) {
            getLogger().warning("Failed to initialize private message Redis sync: " + exception.getMessage());
            this.privateMessageSyncService = null;
            this.privateMessageManager.seedOnlinePlayers();
        }
    }

    public void reloadHippoStaff(CommandSender sender) {
        reloadConfig();
        this.messageService.reload();
        this.hookManager.initialize();
        initializeVoiceChatSupport();
        initializeRedisSync();
        initializeStaffChatSync();
        initializePrivateMessageSync();
        this.staffChatManager.shutdownDiscord();
        this.staffChatManager.initDiscord();
        this.vanishManager.reloadRuntime();
        this.messageService.send(sender, "hippostaff.reloaded");
    }
}
