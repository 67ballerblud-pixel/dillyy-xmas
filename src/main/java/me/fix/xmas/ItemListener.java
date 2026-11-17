package me.fix.xmas;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Behaviour for the Frost Wand: right-click fires an ice bolt (snowball) that damages and slows. */
public class ItemListener implements Listener {

    private final Xmas plugin;
    private final NamespacedKey boltKey;

    ItemListener(Xmas plugin) {
        this.plugin = plugin;
        this.boltKey = new NamespacedKey(plugin, "frost_bolt");
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (!"frost_wand".equals(Items.idOf(it))) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Material mat = it.getType();
        if (p.hasCooldown(mat)) return;
        p.setCooldown(mat, plugin.getConfig().getInt("items.frost-wand.cooldown-ticks", 60));

        Snowball sb = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.8));
        sb.getPersistentDataContainer().set(boltKey, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 1.4f);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(boltKey, PersistentDataType.BYTE)) return;
        pr.getWorld().spawnParticle(Particle.SNOWFLAKE, pr.getLocation(), 25, 0.3, 0.3, 0.3, 0.05);
        if (!(e.getHitEntity() instanceof LivingEntity target)) return;
        if (!(pr.getShooter() instanceof Player shooter) || target.equals(shooter)) return;
        double dmg = plugin.getConfig().getDouble("items.frost-wand.damage", 3);
        int slow = plugin.getConfig().getInt("items.frost-wand.slow-seconds", 3) * 20;
        target.damage(dmg, shooter);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1));
    }
}
