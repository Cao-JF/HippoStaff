package net.mwtw.hippoStaff.hook;

import net.mwtw.hippoStaff.Core;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class VoiceChatSupport {
    private static final UUID NO_PREVIOUS_GROUP = new UUID(0L, 0L);
    private static final AtomicReference<Object> SERVER_API = new AtomicReference<>();

    private final Core plugin;
    private final boolean enabled;
    private final Map<UUID, UUID> previousGroups;
    private Object forcedGroup;

    public VoiceChatSupport(Core plugin) {
        this.plugin = plugin;
        this.enabled = this.plugin.getConfig().getBoolean("vanish.simple-voice-chat.enabled", true);
        this.previousGroups = new ConcurrentHashMap<>();
    }

    public static boolean registerApiHook(Core plugin) {
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> serviceClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService", false, loader);
            Object service = plugin.getServer().getServicesManager().load(serviceClass);
            if (service == null) {
                return false;
            }
            Class<?> pluginInterface = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin", false, loader);
            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{pluginInterface}, new VoicechatPluginHandler());
            Method registerPlugin = serviceClass.getMethod("registerPlugin", pluginInterface);
            registerPlugin.invoke(service, proxy);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void apply(Player player, boolean vanished) {
        if (!this.enabled) {
            return;
        }
        Object api = SERVER_API.get();
        if (api == null) {
            return;
        }
        Object connection = getConnection(api, player.getUniqueId());
        if (connection == null) {
            return;
        }
        if (vanished) {
            forceGroup(api, connection, player.getUniqueId());
            return;
        }
        restoreGroup(api, connection, player.getUniqueId());
    }

    public void clear(Player player) {
        if (!this.enabled) {
            return;
        }
        Object api = SERVER_API.get();
        if (api == null) {
            return;
        }
        Object connection = getConnection(api, player.getUniqueId());
        if (connection != null) {
            restoreGroup(api, connection, player.getUniqueId());
        }
    }

    public void shutdown() {
        if (!this.enabled) {
            return;
        }
        Object api = SERVER_API.get();
        if (api == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object connection = getConnection(api, player.getUniqueId());
            if (connection != null) {
                restoreGroup(api, connection, player.getUniqueId());
            }
        }
        this.previousGroups.clear();
    }

    private void forceGroup(Object api, Object connection, UUID playerUuid) {
        Object current = invoke(connection, "getGroup");
        if (current != null) {
            UUID currentId = (UUID) invoke(current, "getId");
            this.previousGroups.putIfAbsent(playerUuid, currentId);
        } else {
            this.previousGroups.putIfAbsent(playerUuid, NO_PREVIOUS_GROUP);
        }

        Object targetGroup = resolveOrCreateForcedGroup(api);
        if (targetGroup == null) {
            return;
        }
        invoke(connection, "setGroup", targetGroup);
    }

    private void restoreGroup(Object api, Object connection, UUID playerUuid) {
        UUID previous = this.previousGroups.remove(playerUuid);
        if (previous == null || NO_PREVIOUS_GROUP.equals(previous)) {
            invoke(connection, "setGroup", new Class<?>[]{findVoicechatGroupClass()}, new Object[]{null});
            return;
        }
        Object previousGroup = invoke(api, "getGroup", previous);
        invoke(connection, "setGroup", previousGroup);
    }

    private Object resolveOrCreateForcedGroup(Object api) {
        if (this.forcedGroup != null) {
            UUID forcedId = (UUID) invoke(this.forcedGroup, "getId");
            Object existing = invoke(api, "getGroup", forcedId);
            if (existing != null) {
                return this.forcedGroup;
            }
        }

        String name = this.plugin.getConfig().getString("vanish.simple-voice-chat.force-group.name", "Staff Vanish");
        String typeRaw = this.plugin.getConfig().getString("vanish.simple-voice-chat.force-group.type", "ISOLATED");
        boolean persistent = this.plugin.getConfig().getBoolean("vanish.simple-voice-chat.force-group.persistent", true);
        boolean hidden = this.plugin.getConfig().getBoolean("vanish.simple-voice-chat.force-group.hidden", true);
        String password = this.plugin.getConfig().getString("vanish.simple-voice-chat.force-group.password", "");

        Object builder = invoke(api, "groupBuilder");
        if (builder == null) {
            return null;
        }
        Object type = resolveGroupType(typeRaw);
        invoke(builder, "setName", name);
        if (type != null) {
            invoke(builder, "setType", type);
        }
        invoke(builder, "setPersistent", persistent);
        invoke(builder, "setHidden", hidden);
        if (password != null && !password.isBlank()) {
            invoke(builder, "setPassword", password);
        } else {
            invoke(builder, "setPassword", new Class<?>[]{String.class}, new Object[]{null});
        }
        this.forcedGroup = invoke(builder, "build");
        return this.forcedGroup;
    }

    private Object resolveGroupType(String typeRaw) {
        try {
            Class<?> groupTypeClass = Class.forName("de.maxhenkel.voicechat.api.Group$Type", false, this.plugin.getClass().getClassLoader());
            String normalized = typeRaw == null ? "ISOLATED" : typeRaw.toUpperCase(java.util.Locale.ROOT);
            try {
                return Enum.valueOf((Class<Enum>) groupTypeClass.asSubclass(Enum.class), normalized);
            } catch (IllegalArgumentException ignored) {
                return Enum.valueOf((Class<Enum>) groupTypeClass.asSubclass(Enum.class), "ISOLATED");
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object getConnection(Object api, UUID uuid) {
        return invoke(api, "getConnectionOf", uuid);
    }

    private static Object invoke(Object target, String method, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        return invoke(target, method, types, args);
    }

    private static Object invoke(Object target, String method, Class<?>[] explicitTypes, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method selected = null;
            try {
                selected = target.getClass().getMethod(method, explicitTypes);
            } catch (NoSuchMethodException ignored) {
            }
            if (selected == null) {
                for (Method candidate : target.getClass().getMethods()) {
                    if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                        selected = candidate;
                        break;
                    }
                }
            }
            if (selected == null) {
                return null;
            }
            return selected.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> findVoicechatGroupClass() {
        try {
            return Class.forName("de.maxhenkel.voicechat.api.Group", false, VoiceChatSupport.class.getClassLoader());
        } catch (Throwable ignored) {
            return Object.class;
        }
    }

    private static final class VoicechatPluginHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getPluginId".equals(name)) {
                return "hippostaff";
            }
            if ("initialize".equals(name) && args != null && args.length == 1 && args[0] != null) {
                SERVER_API.set(args[0]);
                return null;
            }
            return null;
        }
    }
}
