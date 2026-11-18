package my.dangtab;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;

public class DangTab extends JavaPlugin implements Listener {

    private static final String FOOTER =
            "\n\u00a79/help | /discord | /rules\n";
    private static final String TEAM_PREFIX = "dt";

    private Method unlist;
    private Method list;
    private Object chat;
    private Method getPrefix;
    private final Set<String> hiddenPairs = new HashSet<>();

    @Override
    public void onEnable() {
        try {
            unlist = Player.class.getMethod("unlistPlayer", Player.class);
            list = Player.class.getMethod("listPlayer", Player.class);
        } catch (Throwable t) {
            getLogger().warning("This server can't hide names from the tab list.");
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        // Refresh every 1 second (20 ticks)
        Bukkit.getScheduler().runTaskTimer(this, this::refreshAll, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player v : Bukkit.getOnlinePlayers()) {
            for (Player t : Bukkit.getOnlinePlayers()) setListed(v, t, true);
            v.setPlayerListName(null);
            v.setPlayerListHeaderFooter("", "");
            try {
                for (Team t : v.getScoreboard().getTeams()) {
                    if (t.getName().startsWith(TEAM_PREFIX)) t.unregister();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, this::refreshAll, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String id = e.getPlayer().getUniqueId().toString();
        hiddenPairs.removeIf(k -> k.contains(id));
        Bukkit.getScheduler().runTaskLater(this, this::refreshAll, 2L);
    }

    // ---------- LuckPerms data ----------
    private Object luckMeta(Player p) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object lp = provider.getMethod("get").invoke(null);

            Class<?> lpClass = Class.forName("net.luckperms.api.LuckPerms");
            Object adapter = lpClass.getMethod("getPlayerAdapter", Class.class).invoke(lp, Player.class);

            Class<?> adapterClass = Class.forName("net.luckperms.api.platform.PlayerAdapter");
            Object user = adapterClass.getMethod("getUser", Object.class).invoke(adapter, p);

            Class<?> holder = Class.forName("net.luckperms.api.model.PermissionHolder");
            Object cached = holder.getMethod("getCachedData").invoke(user);

            Class<?> cachedClass = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            return cachedClass.getMethod("getMetaData").invoke(cached);
        } catch (Throwable t) {
            return null;
        }
    }

    // Prefix priority (the number in "meta setprefix <number>")
    private int priorityOf(Player p) {
        try {
            Object meta = luckMeta(p);
            if (meta == null) return 0;
            Class<?> metaClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Object map = metaClass.getMethod("getPrefixes").invoke(meta);
            if (map instanceof SortedMap<?, ?> sm && !sm.isEmpty()) {
                return ((Number) sm.firstKey()).intValue();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private String luckPermsPrefix(Player p) {
        try {
            Object meta = luckMeta(p);
            if (meta == null) return "";
            Class<?> metaClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Object prefix = metaClass.getMethod("getPrefix").invoke(meta);
            return prefix == null ? "" : prefix.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // Tier text from LuckPerms meta key "tier", for example "&6[HT4]"
    private String tierOf(Player p) {
        try {
            Object meta = luckMeta(p);
            if (meta == null) return "";
            Class<?> metaClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Object v = metaClass.getMethod("getMetaValue", String.class).invoke(meta, "tier");
            return v == null ? "" : v.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    // Rank prefix: LuckPerms first, Vault as a fallback
    private String rankOf(Player p) {
        String r = luckPermsPrefix(p);
        if (r == null || r.isEmpty()) r = vaultPrefix(p);
        if (r == null) return "";
        return r;
    }

    // Full prefix = rank + tier, for example "[Member] [HT4] "
    private String prefixOf(Player p) {
        String rank = rankOf(p);
        String tier = tierOf(p);
        String full = rank;
        if (!tier.isEmpty()) {
            if (!rank.isEmpty() && !rank.endsWith(" ")) full += " ";
            full += tier + " ";
        }
        return ChatColor.translateAlternateColorCodes('&', full);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String vaultPrefix(Player p) {
        try {
            if (chat == null) {
                Class c = Class.forName("net.milkbowl.vault.chat.Chat");
                RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration(c);
                if (rsp == null) return "";
                chat = rsp.getProvider();
                getPrefix = c.getMethod("getPlayerPrefix", Player.class);
            }
            Object r = getPrefix.invoke(chat, p);
            return r == null ? "" : r.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------- Tab order + prefix above the head ----------
    // Higher prefix priority = higher in the tab list. The team prefix is what
    // shows above the player's head.
    private void applyTeam(Player viewer, Player target, String prefix) {
        try {
            Scoreboard sb = viewer.getScoreboard();
            int pr = Math.max(0, Math.min(priorityOf(target), 9999));
            String hash = String.format("%04x", Math.abs(prefix.hashCode()) % 65536);
            String teamName = TEAM_PREFIX + String.format("%04d", 9999 - pr) + hash;

            Team team = sb.getTeam(teamName);
            if (team == null) team = sb.registerNewTeam(teamName);

            String wanted = prefix.isEmpty() ? "" : prefix + "\u00a7r";
            if (!wanted.equals(team.getPrefix())) team.setPrefix(wanted);

            String entry = target.getName();
            Team current = sb.getEntryTeam(entry);
            if (current != null && current.equals(team)) return;
            team.addEntry(entry);
        } catch (Throwable ignored) {
        }
    }

    // VanishLite uses the invisibility effect. Only staff (OP) count as vanished.
    private boolean isVanished(Player p) {
        return p.isOp() && p.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void setListed(Player viewer, Player target, boolean listed) {
        if (unlist == null || list == null || viewer == target) return;
        String key = viewer.getUniqueId() + ":" + target.getUniqueId();
        try {
            if (!listed) {
                unlist.invoke(viewer, target);
                hiddenPairs.add(key);
            } else if (hiddenPairs.remove(key)) {
                list.invoke(viewer, target);
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshAll() {
        Set<Player> vanished = new HashSet<>();
        int online = 0;
        int staff = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isVanished(p)) {
                vanished.add(p);
                continue;
            }
            online++;
            if (p.isOp()) staff++;
        }

        String header = "\n\u00a7f\u00a7lDang\u00a7a\u00a7lCraft\n"
                + "\u00a77Online: \u00a7a" + online
                + " \u00a78| \u00a77Staff: \u00a7b" + staff + "\n";

        for (Player p : Bukkit.getOnlinePlayers()) {
            int ping = p.getPing();
            String color = ping < 100 ? "\u00a7a" : ping < 200 ? "\u00a7e" : "\u00a7c";
            p.setPlayerListName(prefixOf(p) + "\u00a7f" + p.getName()
                    + " \u00a78[" + color + ping + "ms\u00a78]");
            p.setPlayerListHeaderFooter(header, FOOTER);
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                applyTeam(viewer, target, prefixOf(target));
                boolean hide = vanished.contains(target) && !viewer.isOp();
                setListed(viewer, target, !hide);
            }
        }
    }
        }
