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

public class ConfirmDeleteMenu implements InventoryHolder {

    private static InvestmentsPlugin plugin;
    private static FileConfiguration guiConfig;

    private static String title = "&cConfirm Investment Deletion";
    private static int size = 27;

    private static boolean fillerEnabled = true;
    private static Material fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private static String fillerName = "&7";
    private static List<String> fillerLore = Collections.emptyList();

    private static int yesSlot = 11;
    private static int noSlot = 15;

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

        // Base GUI
        title = guiConfig.getString("confirm-delete-gui.title", "&cConfirm Investment Deletion");
        size = guiConfig.getInt("confirm-delete-gui.size", 27);

        // Filler
        ConfigurationSection fillerSec = guiConfig.getConfigurationSection("confirm-delete-gui.filler");
        if (fillerSec != null) {
            fillerEnabled = fillerSec.getBoolean("enabled", true);
            String matName = fillerSec.getString("material", "GRAY_STAINED_GLASS_PANE");
            Material mat = Material.matchMaterial(matName != null ? matName.trim() : "GRAY_STAINED_GLASS_PANE");
            if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
            fillerMaterial = mat;
            fillerName = fillerSec.getString("name", "&7");
            fillerLore = new ArrayList<>(fillerSec.getStringList("lore"));
        } else {
            fillerEnabled = true;
            fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            fillerName = "&7";
            fillerLore = Collections.emptyList();
        }

        yesSlot = guiConfig.getInt("confirm-delete-gui.yes-button.slot", 11);
        noSlot  = guiConfig.getInt("confirm-delete-gui.no-button.slot", 15);

        plugin.getLogger().info("[Investments] ConfirmDeleteMenu config reloaded. yesSlot=" +
                yesSlot + ", noSlot=" + noSlot);
    }

    public static void openFor(Player player, InvestmentProfile profile) {
        ConfirmDeleteMenu menu = new ConfirmDeleteMenu(profile);
        player.openInventory(menu.getInventory());
    }

    public ConfirmDeleteMenu(InvestmentProfile profile) {
        this.profile = profile;
        this.inventory = Bukkit.createInventory(this, size, MessageUtils.color(title));
        build();
    }

    private void build() {
        // Stats for placeholders
        BigDecimal totalInv = profile.getTotalInvested();
        BigDecimal totalProf = profile.getTotalProfit();
        int count = profile.getInvestments().size();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("total_invested", totalInv.toPlainString());
        placeholders.put("total_profit", totalProf.toPlainString());
        placeholders.put("count", String.valueOf(count));

        // Filler – fill everything except YES/NO slots
        if (fillerEnabled) {
            ItemStack filler = new ItemStack(fillerMaterial);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName(MessageUtils.color(fillerName));
            if (fillerLore != null && !fillerLore.isEmpty()) {
                List<String> loreColored = new ArrayList<>();
                for (String line : fillerLore) {
                    loreColored.add(MessageUtils.color(line));
                }
                meta.setLore(loreColored);
            }
            filler.setItemMeta(meta);

            for (int i = 0; i < inventory.getSize(); i++) {
                if (i == yesSlot || i == noSlot) continue;
                inventory.setItem(i, filler);
            }
        }

        // YES item from confirm-delete-gui.yes-button
        ConfigurationSection yesSec = guiConfig.getConfigurationSection("confirm-delete-gui.yes-button");
        if (yesSec != null && yesSlot >= 0 && yesSlot < inventory.getSize()) {
            ItemStack yesItem = buildItem(yesSec, placeholders);
            inventory.setItem(yesSlot, yesItem);
        }

        // NO item from confirm-delete-gui.no-button
        ConfigurationSection noSec = guiConfig.getConfigurationSection("confirm-delete-gui.no-button");
        if (noSec != null && noSlot >= 0 && noSlot < inventory.getSize()) {
            ItemStack noItem = buildItem(noSec, placeholders);
            inventory.setItem(noSlot, noItem);
        }
    }

    private ItemStack buildItem(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) return null;

        String matName = section.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName != null ? matName.trim() : "STONE");
        if (mat == null) mat = Material.STONE;

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();

        String name = section.getString("name", "");
        meta.setDisplayName(apply(name, placeholders));

        List<String> loreLines = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            loreLines.add(apply(line, placeholders));
        }
        if (!loreLines.isEmpty()) {
            meta.setLore(loreLines);
        }

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

    public static int getYesSlot() {
        return yesSlot;
    }

    public static int getNoSlot() {
        return noSlot;
    }

    public InvestmentProfile getProfile() {
        return profile;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

