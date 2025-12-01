package com.jamplifier.investments.gui;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ConfirmDeleteMenu implements InventoryHolder {

    private static final int SIZE = 27; // 3 rows
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT  = 15;

    private final InvestmentsPlugin plugin;
    private final Inventory inventory;

    private ConfirmDeleteMenu(InvestmentsPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(
                this,
                SIZE,
                MessageUtils.color("&cConfirm Investment Deletion")
        );
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static int getConfirmSlot() {
        return CONFIRM_SLOT;
    }

    public static int getCancelSlot() {
        return CANCEL_SLOT;
    }

    public static void openFor(InvestmentsPlugin plugin, Player player, InvestmentProfile profile) {
        ConfirmDeleteMenu menu = new ConfirmDeleteMenu(plugin);
        menu.populate(player, profile);
        player.openInventory(menu.getInventory());
    }

    private void populate(Player player, InvestmentProfile profile) {
        // Fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        fMeta.setDisplayName(" ");
        filler.setItemMeta(fMeta);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Some info about what they're about to delete
        BigDecimal totalInvested = profile.getTotalInvested();
        BigDecimal totalProfit = profile.getTotalProfit();

        // Confirm button
        ItemStack confirm = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cMeta = confirm.getItemMeta();
        cMeta.setDisplayName(MessageUtils.color("&c&lConfirm Delete"));
        List<String> cLore = new ArrayList<>();
        cLore.add(MessageUtils.color("&7This will &cdelete all investments"));
        cLore.add(MessageUtils.color("&7for &f" + player.getName() + "&7!"));
        cLore.add("");
        cLore.add(MessageUtils.color("&7Total Invested: &a" + totalInvested.toPlainString()));
        cLore.add(MessageUtils.color("&7Total Profit: &a" + totalProfit.toPlainString()));
        cLore.add("");
        cLore.add(MessageUtils.color("&cThis action cannot be undone."));
        cMeta.setLore(cLore);
        confirm.setItemMeta(cMeta);
        inventory.setItem(CONFIRM_SLOT, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta kMeta = cancel.getItemMeta();
        kMeta.setDisplayName(MessageUtils.color("&aCancel"));
        List<String> kLore = new ArrayList<>();
        kLore.add(MessageUtils.color("&7Go back without deleting."));
        kMeta.setLore(kLore);
        cancel.setItemMeta(kMeta);
        inventory.setItem(CANCEL_SLOT, cancel);
    }
}
