package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class Snow {

    private final Xmas plugin;

    Snow(Xmas plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private void tick() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("snow.enabled", true)) return;
        List<String> worlds = cfg.getStringList("snow.worlds");
        boolean nearSpawn = cfg.getBoolean("snow.only-near-spawn", true);
        double radius = cfg.getDouble("snow.spawn-radius", 150);
        int density = cfg.getInt("snow.density", 25);

        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (!worlds.contains(w.getName()) || plugin.snowOff(p.getUniqueId())) continue;
            Location l = p.getLocation();
            if (nearSpawn) {
                Location s = w.getSpawnLocation();
                double dx = l.getX() - s.getX();
                double dz = l.getZ() - s.getZ();
                if (dx * dx + dz * dz > radius * radius) continue;
            }
            // only where the sky is visible (no snow inside caves/buildings)
            if (l.getBlock().getLightFromSky() < 12) continue;
            p.spawnParticle(Particle.SNOWFLAKE, l.getX(), l.getY() + 6, l.getZ(), density, 8, 3, 8, 0.0);
        }
    }
}
