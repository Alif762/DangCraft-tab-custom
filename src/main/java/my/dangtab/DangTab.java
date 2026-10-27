package my.dangtab;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class DangTab extends JavaPlugin implements Listener {

    private static final String HEADER =
            "\n\u00a7f\u00a7lDang\u00a7a\u00a7lCraft\n";
    private static final String FOOTER =
            "\n\u00a79/help | /discord | /rules\n";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // Refresh setiap 2 saat (40 tick)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 20L, 40L);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setPlayerListName(null);
            p.setPlayerListHeaderFooter("", "");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> update(e.getPlayer()), 5L);
    }

    private void update(Player p) {
        int ping = p.getPing();
        String color = ping < 100 ? "\u00a7a" : ping < 200 ? "\u00a7e" : "\u00a7c";
        p.setPlayerListName("\u00a7f" + p.getName()
                + " \u00a78[" + color + ping + "ms\u00a78]");
        p.setPlayerListHeaderFooter(HEADER, FOOTER);
    }
}
