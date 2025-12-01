package com.jamplifier.investments.gui;

import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.util.GuiClickGuard;
import com.jamplifier.investments.util.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ConfirmDeleteMenuListener implements Listener {

    private final InvestmentManager investmentManager;

    public ConfirmDeleteMenuListener(InvestmentManager investmentManager) {
        this.investmentManager = investmentManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmDeleteMenu menu)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        if (com.jamplifier.investments.util.GuiClickGuard.shouldBlock(player)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        // YES -> delete
        if (rawSlot == ConfirmDeleteMenu.getYesSlot()) {
            player.closeInventory();

            investmentManager.deleteInvestments(player);
            MessageUtils.send(player, "investment-deleted");

            // Optionally re-open main menu
            InvestmentProfile profile = investmentManager.getProfile(player.getUniqueId());
            InvestmentsMenu.openFor(player, profile);
            return;
        }

        // NO -> cancel
        if (rawSlot == ConfirmDeleteMenu.getNoSlot()) {
            player.closeInventory();
            // Just re-open normal menu
            UUID uuid = player.getUniqueId();
            InvestmentProfile profile = investmentManager.getProfile(uuid);
            InvestmentsMenu.openFor(player, profile);
        }
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GuiClickGuard.clear(event.getPlayer());
    }

}
