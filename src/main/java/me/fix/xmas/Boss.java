package me.fix.xmas;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** The Grinch: a Christmas raid boss. Spawn with /xmas boss spawn (or enable auto-spawn in config). */
public class Boss implements Listener {

    private final Xmas plugin;
    private Zombie boss;
    private BossBar bar;
    private BukkitTask loop;
    private long spawnedAt;
    private int seconds;
    private boolean enraged;
    private long nextAuto;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Set<UUID> minions = new HashSet<>();
    private final Set<UUID> shown = new HashSet<>();

    Boss(Xmas plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    void start() {
        nextAuto = System.currentTimeMillis() + autoMs();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!cfg().getBoolean("boss.auto-spawn.enabled", false) || active()) return;
            if (System.currentTimeMillis() < nextAuto) return;
            nextAuto = System.currentTimeMillis() + autoMs();
            if (Bukkit.getOnlinePlayers().size() < cfg().getInt("boss.auto-spawn.min-online", 3)) return;
            Location l = configLocation();
            if (l != null) spawn(l);
        }, 200L, 200L);
    }

    private long autoMs() {
        return Math.max(1, cfg().getLong("boss.auto-spawn.interval-minutes", 120)) * 60_000L;
    }

    Location configLocation() {
        World w = Bukkit.getWorld(cfg().getString("boss.location.world", "world"));
        if (w == null) return null;
        return new Location(w, cfg().getDouble("boss.location.x", 0), cfg().getDouble("boss.location.y", 100),
                cfg().getDouble("boss.location.z", 0));
    }

    boolean active() {
        return boss != null && boss.isValid() && !boss.isDead();
    }

    String info() {
        if (!active()) return "No boss is active.";
        Location l = boss.getLocation();
        return "The Grinch: " + (int) boss.getHealth() + " HP at " + l.getBlockX() + ", " + l.getBlockY() + ", "
                + l.getBlockZ() + " (" + l.getWorld().getName() + ")";
    }

    private void setAttr(LivingEntity e, Attribute a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private ItemStack leather(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(c);
            it.setItemMeta(lm);
        }
        return it;
    }

    // ---------- spawning ----------
    boolean spawn(Location loc) {
        if (active() || loc.getWorld() == null) return false;
        String name = cfg().getString("boss.name", "&a&lThe Grinch");
        double health = cfg().getDouble("boss.health", 1000);

        boss = loc.getWorld().spawn(loc, Zombie.class, z -> {
            z.setBaby(false);
            z.customName(Xmas.c(name));
            z.setCustomNameVisible(true);
            z.setPersistent(false);          // never saved to disk, so no orphan bosses after a restart
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);
            setAttr(z, Attribute.MAX_HEALTH, health);
            z.setHealth(health);
            setAttr(z, Attribute.ATTACK_DAMAGE, cfg().getDouble("boss.damage", 8));
            setAttr(z, Attribute.MOVEMENT_SPEED, cfg().getDouble("boss.speed", 0.28));
            setAttr(z, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            setAttr(z, Attribute.SCALE, cfg().getDouble("boss.scale", 2.0));
            setAttr(z, Attribute.FOLLOW_RANGE, 48);
            EntityEquipment eq = z.getEquipment();
            eq.setHelmet(leather(Material.LEATHER_HELMET, Color.GREEN));
            eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.RED));
            eq.setLeggings(leather(Material.LEATHER_LEGGINGS, Color.GREEN));
            eq.setBoots(leather(Material.LEATHER_BOOTS, Color.RED));
            ItemStack sword = Items.create("candy_cane_sword", 1);
            if (sword != null) eq.setItemInMainHand(sword);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        });

        damage.clear();
        minions.clear();
        shown.clear();
        enraged = false;
        seconds = 0;
        spawnedAt = System.currentTimeMillis();
        bar = BossBar.bossBar(Xmas.c(name), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.6f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 80, 2, 1, 2, 0.1);
        if (cfg().getBoolean("boss.broadcast-spawn", true)) {
            Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "") + "&a&lThe Grinch &fis trying to ruin Christmas at &eX "
                    + loc.getBlockX() + ", Y " + loc.getBlockY() + ", Z " + loc.getBlockZ() + " &f(" + loc.getWorld().getName()
                    + ")! Stop him!"));
        }
        return true;
    }

    /** Remove the boss and everything it spawned without giving rewards. */
    void remove() {
        if (boss != null && boss.isValid()) boss.remove();
        cleanup();
    }

    private void cleanup() {
        if (loop != null) loop.cancel();
        loop = null;
        if (bar != null) {
            for (UUID id : shown) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(bar);
            }
        }
        for (UUID id : minions) {
            Entity m = Bukkit.getEntity(id);
            if (m != null) m.remove();
        }
        minions.clear();
        shown.clear();
        damage.clear();
        boss = null;
        bar = null;
        enraged = false;
        seconds = 0;
    }

    // ---------- main loop ----------
    private void tick() {
        if (!active()) {
            cleanup();
            return;
        }
        seconds++;
        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hp = boss.getHealth();
        bar.progress((float) Math.max(0, Math.min(1, hp / max)));
        bar.name(Xmas.c(cfg().getString("boss.name", "&a&lThe Grinch") + " &7- &c" + (int) hp + "&7/&c" + (int) max));

        Location bl = boss.getLocation();
        for (Player p : boss.getWorld().getPlayers()) {
            boolean near = p.getLocation().distanceSquared(bl) < 80 * 80;
            if (near && shown.add(p.getUniqueId())) p.showBossBar(bar);
            else if (!near && shown.remove(p.getUniqueId())) p.hideBossBar(bar);
        }

        if (!enraged && hp < max * 0.3) enrage();

        long lifeMs = Math.max(1, cfg().getLong("boss.max-lifetime-minutes", 20)) * 60_000L;
        if (System.currentTimeMillis() - spawnedAt > lifeMs) {
            Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "") + "&a&lThe Grinch &fgot bored and went back to his cave."));
            remove();
            return;
        }

        int interval = Math.max(2, cfg().getInt("boss.ability-interval-seconds", 7));
        if (enraged) interval = Math.max(2, interval / 2);
        if (seconds % interval == 0) ability();
    }

    private List<Player> nearbyPlayers(double radius) {
        List<Player> out = new ArrayList<>();
        for (Entity e : boss.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                out.add(p);
            }
        }
        return out;
    }

    private void enrage() {
        enraged = true;
        setAttr(boss, Attribute.MOVEMENT_SPEED, cfg().getDouble("boss.speed", 0.28) + 0.07);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3f, 0.5f);
        for (Player p : nearbyPlayers(80)) {
            p.sendMessage(Xmas.c("&c&lThe Grinch is ENRAGED! &7He moves faster and attacks more often!"));
        }
    }

    private void ability() {
        List<Player> targets = nearbyPlayers(30);
        if (targets.isEmpty()) return;
        int pick = ThreadLocalRandom.current().nextInt(3);
        if (pick == 2 && minions.size() >= cfg().getInt("boss.max-minions", 6)) pick = ThreadLocalRandom.current().nextInt(2);
        switch (pick) {
            case 0 -> stomp();
            case 1 -> snowballs(targets);
            default -> elves(targets);
        }
    }

    private void stomp() {
        Location l = boss.getLocation();
        World w = l.getWorld();
        w.spawnParticle(Particle.CLOUD, l, 70, 3, 0.2, 3, 0.1);
        w.spawnParticle(Particle.SNOWFLAKE, l, 60, 3, 0.5, 3, 0.05);
        w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.7f);
        double dmg = cfg().getDouble("boss.stomp-damage", 6);
        for (Player p : nearbyPlayers(7)) {
            p.damage(dmg, boss);
            Vector away = p.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (away.lengthSquared() < 0.0001) away = new Vector(1, 0, 0);
            away.normalize().multiply(1.2).setY(0.7);
            p.setVelocity(away);
            p.sendActionBar(Xmas.c("&c&lSTOMP!"));
        }
    }

    private void snowballs(List<Player> targets) {
        targets.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation())));
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 2f, 0.6f);
        for (int i = 0; i < Math.min(3, targets.size()); i++) {
            Player t = targets.get(i);
            Vector dir = t.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
            if (dir.lengthSquared() < 0.0001) continue;
            boss.launchProjectile(Snowball.class, dir.normalize().multiply(1.6));
            t.sendActionBar(Xmas.c("&b&lSNOWBALLS incoming!"));
        }
    }

    private void elves(List<Player> targets) {
        Location l = boss.getLocation();
        for (int i = 0; i < 3 && minions.size() < cfg().getInt("boss.max-minions", 6); i++) {
            Location spawn = l.clone().add(ThreadLocalRandom.current().nextDouble(-3, 3), 0, ThreadLocalRandom.current().nextDouble(-3, 3));
            Zombie elf = l.getWorld().spawn(spawn, Zombie.class, z -> {
                z.setBaby(true);
                z.customName(Xmas.c("&2Grinch's Elf"));
                z.setCustomNameVisible(true);
                z.setPersistent(false);
                z.setRemoveWhenFarAway(true);
                z.setShouldBurnInDay(false);
                setAttr(z, Attribute.MAX_HEALTH, 20);
                z.setHealth(20);
                z.getEquipment().setHelmet(leather(Material.LEATHER_HELMET, Color.GREEN));
                z.getEquipment().setHelmetDropChance(0f);
            });
            elf.setTarget(targets.get(ThreadLocalRandom.current().nextInt(targets.size())));
            minions.add(elf.getUniqueId());
        }
        l.getWorld().playSound(l, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 1f);
        for (Player p : targets) p.sendActionBar(Xmas.c("&2&lThe Grinch calls his elves!"));
    }

    // ---------- events ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!active() || !e.getEntity().getUniqueId().equals(boss.getUniqueId())) return;
        Player p = null;
        Entity d = e.getDamager();
        if (d instanceof Player direct) p = direct;
        else if (d instanceof Projectile pr && pr.getShooter() instanceof Player shooter) p = shooter;
        if (p != null) damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler
    public void onSnowballHit(ProjectileHitEvent e) {
        if (!active() || !(e.getEntity() instanceof Snowball sb)) return;
        if (!(sb.getShooter() instanceof Entity shooter) || !shooter.getUniqueId().equals(boss.getUniqueId())) return;
        if (e.getHitEntity() instanceof Player p) {
            p.damage(cfg().getDouble("boss.snowball-damage", 3), boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if ((boss != null && id.equals(boss.getUniqueId())) || minions.contains(id)) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (boss != null && id.equals(boss.getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(cfg().getInt("boss.xp", 500));
            finish();
        } else if (minions.remove(id)) {
            e.getDrops().clear();
            e.setDroppedExp(5);
        }
    }

    private void finish() {
        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(damage.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double min = cfg().getDouble("boss.min-damage-to-reward", 20);
        List<String> participation = cfg().getStringList("boss.participation-commands");
        ConfigurationSection top = cfg().getConfigurationSection("boss.top-commands");

        StringBuilder board = new StringBuilder();
        for (int i = 0; i < ranked.size(); i++) {
            Player p = Bukkit.getPlayer(ranked.get(i).getKey());
            if (p == null) continue;
            double dmg = ranked.get(i).getValue();
            if (dmg >= min) plugin.runCommands(p, participation);
            if (i < 3) {
                if (top != null) plugin.runCommands(p, top.getStringList(String.valueOf(i + 1)));
                board.append("&e#").append(i + 1).append(" &f").append(p.getName()).append(" &7(").append((int) dmg).append(") ");
            }
        }
        Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "") + "&a&lThe Grinch &fwas defeated! " + board));
        cleanup();
    }
}
