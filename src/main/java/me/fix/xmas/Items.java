package me.fix.xmas;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Custom Christmas items. Get them with /xmas give <player> <id> [amount] (the crate does this too). */
public final class Items {

    public static final List<String> IDS = List.of(
            "santa_hat", "reindeer_boots", "candy_cane_sword", "candy_cane_pickaxe", "frost_wand", "grinch_heart");

    private static NamespacedKey key;

    private Items() {
    }

    static void init(Xmas plugin) {
        key = new NamespacedKey(plugin, "xmas_item");
    }

    static String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static ItemStack build(String id, Material m, String name, Color color, List<String> lore,
                                   Map<Enchantment, Integer> ench) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Xmas.line(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Xmas.line(s));
        meta.lore(l);
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) meta.addEnchant(e.getKey(), e.getValue(), true);
        if (color != null && meta instanceof LeatherArmorMeta lm) lm.setColor(color);
        meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private static Map<Enchantment, Integer> ench(Object... pairs) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    static ItemStack create(String id, int amount) {
        ItemStack it;
        switch (id) {
            case "santa_hat" -> it = build(id, Material.LEATHER_HELMET, "&c&lSanta Hat", Color.RED,
                    List.of("&7Ho ho ho!", "", "&8Christmas Exclusive"),
                    ench(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.RESPIRATION, 3));
            case "reindeer_boots" -> it = build(id, Material.LEATHER_BOOTS, "&6&lReindeer Boots", Color.fromRGB(139, 69, 19),
                    List.of("&7Fast as Rudolph.", "", "&8Christmas Exclusive"),
                    ench(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3,
                            Enchantment.UNBREAKING, 3));
            case "candy_cane_sword" -> it = build(id, Material.DIAMOND_SWORD, "&c&lCandy &f&lCane &c&lBlade", null,
                    List.of("&7Sweet, sharp, and a little sticky.", "", "&8Christmas Exclusive"),
                    ench(Enchantment.SHARPNESS, 4, Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3));
            case "candy_cane_pickaxe" -> it = build(id, Material.DIAMOND_PICKAXE, "&c&lCandy &f&lCane &c&lPickaxe", null,
                    List.of("&7Digs through coal faster than Santa's list.", "", "&8Christmas Exclusive"),
                    ench(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3));
            case "frost_wand" -> it = build(id, Material.BLAZE_ROD, "&b&lFrost Wand", null,
                    List.of("&7Right-click to fire an ice bolt.", "&7Slows whatever it hits.", "", "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "grinch_heart" -> it = build(id, Material.HEART_OF_THE_SEA, "&2&lGrinch's Heart", null,
                    List.of("&7Two sizes too small... until now.", "&7A trophy from defeating &aThe Grinch&7.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            default -> {
                return null;
            }
        }
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }
}
