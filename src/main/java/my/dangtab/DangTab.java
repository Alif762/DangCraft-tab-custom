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

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class DangTab extends JavaPlugin implements Listener {

    private static final String FOOTER =
            "\n\u00a79/help | /discord | /rules\n";

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
            getLogger().warning("Server ini tak sokong sembunyi nama dalam tab.");
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        // Refresh setiap 1 saat (20 tick)
        Bukkit.getScheduler().runTaskTimer(this, this::refreshAll, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player v : Bukkit.getOnlinePlayers()) {
            for (Player t : Bukkit.getOnlinePlayers()) setListed(v, t, true);
            v.setPlayerListName(null);
            v.setPlayerListHeaderFooter("", "");
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

    // Ambil rank (prefix) dari LuckPerms melalui Vault
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String prefixOf(Player p) {
        try {
            if (chat == null) {
                Class c = Class.forName("net.milkbowl.vault.chat.Chat");
                RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration(c);
                if (rsp == null) return "";
                chat = rsp.getProvider();
                getPrefix = c.getMethod("getPlayerPrefix", Player.class);
            }
            Object r = getPrefix.invoke(chat, p);
            return r == null ? "" : ChatColor.translateAlternateColorCodes('&', r.toString());
        } catch (Throwable t) {
            return "";
        }
    }

    // VanishLite guna efek invisibility. Hanya staff (OP) yang dikira vanish.
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

        // Sembunyi pemain vanish daripada pemain biasa, staff masih nampak
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                boolean hide = vanished.contains(target) && !viewer.isOp();
                setListed(viewer, target, !hide);
            }
        }
    }
}
