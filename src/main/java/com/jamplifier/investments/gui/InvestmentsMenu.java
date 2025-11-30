package com.jamplifier.investments.gui;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

public class InvestmentsMenu implements InventoryHolder {

    private static InvestmentsPlugin plugin;
    private static FileConfiguration guiConfig;

    private static String title;
    private static int size;
    private static int deleteSlot;
    private static int infoSlot;
    private static int collectSlot;
    private static int autocollectSlot;

    private static boolean fillerEnabled;
    private static Material fillerMaterial;
    private static String fillerName;
    private static boolean fillerAllSlots;
    private static List<Integer> fillerSlots = new ArrayList<>();

    private final InvestmentProfile profile;
    private final Inventory inventory;

    public static void init(InvestmentsPlugin pl) {
        plugin = pl;
        reloadConfig();
    }

    public static void reloadConfig() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = guiConfig.getConfigurationSection("investments-gui");
        if (root == null) {
            plugin.getLogger().severe("Missing 'investments-gui' section in gui.yml");
            return;
        }

        title = root.getString("title", "&aYour Investments");
        size = root.getInt("size", 27);

        deleteSlot = root.getInt("delete-investment-slot", 11);
        infoSlot = root.getInt("info-slot", 13);
        collectSlot = root.getInt("collect-slot", 15);
        autocollectSlot = root.getInt("autocollect-slot", 24);

        ConfigurationSection fillerSec = root.getConfigurationSection("filler");
        if (fillerSec != null) {
            fillerEnabled = fillerSec.getBoolean("enabled", false);
            fillerMaterial = Material.matchMaterial(fillerSec.getString("material", "GRAY_STAINED_GLASS_PANE"));
            if (fillerMaterial == null) {
                fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            }
            fillerName = fillerSec.getString("name", "&r");

            fillerSlots.clear();
            Object slotsObj = fillerSec.get("slots");
            if (slotsObj instanceof String) {
                fillerAllSlots = ((String) slotsObj).equalsIgnoreCase("all");
            } else if (slotsObj instanceof List<?>) {
                fillerAllSlots = false;
                for (Object o : (List<?>) slotsObj) {
                    try {
                        fillerSlots.add(Integer.parseInt(String.valueOf(o)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public InvestmentsMenu(InvestmentProfile profile) {
        this.profile = profile;
        this.inventory = Bukkit.createInventory(this, size, MessageUtils.color(title));
        build();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public InvestmentProfile getProfile() {
        return profile;
    }

    public static void openFor(Player player, InvestmentProfile profile) {
        InvestmentsMenu menu = new InvestmentsMenu(profile);
        player.openInventory(menu.getInventory());
    }

    public static int getDeleteSlot() {
        return deleteSlot;
    }

    public static int getInfoSlot() {
        return infoSlot;
    }

    public static int getCollectSlot() {
        return collectSlot;
    }

    public static int getAutocollectSlot() {
        return autocollectSlot;
    }

    private void build() {
        ConfigurationSection root = guiConfig.getConfigurationSection("investments-gui");
        if (root == null) {
            return;
        }

        // Fillers
        if (fillerEnabled) {
            ItemStack fillerItem = new ItemStack(fillerMaterial);
            ItemMeta meta = fillerItem.getItemMeta();
            meta.setDisplayName(MessageUtils.color(fillerName));
            fillerItem.setItemMeta(meta);

            if (fillerAllSlots) {
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, fillerItem);
                }
            } else {
                for (int slot : fillerSlots) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        inventory.setItem(slot, fillerItem);
                    }
                }
            }
        }

        BigDecimal invested = profile.getTotalInvested();
        BigDecimal profit = profile.getTotalProfit();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount_invested", invested.toPlainString());
        placeholders.put("profit", profit.toPlainString());
        placeholders.put("autocollect_status",
                profile.isAutoCollect() ? "&aEnabled" : "&cDisabled");

        // Delete
        ItemStack deleteItem = buildItem(root.getConfigurationSection("delete-item"), null);
        if (deleteItem != null && deleteSlot >= 0 && deleteSlot < inventory.getSize()) {
            inventory.setItem(deleteSlot, deleteItem);
        }

        // Info
        ItemStack infoItem = buildItem(root.getConfigurationSection("info-item"), placeholders);
        if (infoItem != null && infoSlot >= 0 && infoSlot < inventory.getSize()) {
            inventory.setItem(infoSlot, infoItem);
        }

        // Collect
        ItemStack collectItem = buildItem(root.getConfigurationSection("collect-item"), placeholders);
        if (collectItem != null && collectSlot >= 0 && collectSlot < inventory.getSize()) {
            inventory.setItem(collectSlot, collectItem);
        }

        // Auto-collect toggle item
        ConfigurationSection autoOn = root.getConfigurationSection("autocollect-item-on");
        ConfigurationSection autoOff = root.getConfigurationSection("autocollect-item-off");
        if (autocollectSlot >= 0 && autocollectSlot < inventory.getSize() && autoOn != null && autoOff != null) {
            ItemStack autoItem = buildItem(profile.isAutoCollect() ? autoOn : autoOff, placeholders);
            inventory.setItem(autocollectSlot, autoItem);
        }
    }

    private ItemStack buildItem(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) return null;

        String matName = section.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.STONE;

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();

        String name = section.getString("name", "");
        meta.setDisplayName(apply(name, placeholders));

        List<String> loreLines = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            loreLines.add(apply(line, placeholders));
        }
        meta.setLore(loreLines);

        stack.setItemMeta(meta);
        return stack;
    }

    private String apply(String line, Map<String, String> placeholders) {
        if (line == null) return "";
        String result = line;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                result = result.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        return MessageUtils.color(result);
    }
}
