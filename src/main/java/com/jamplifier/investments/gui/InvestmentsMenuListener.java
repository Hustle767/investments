package com.jamplifier.investments.gui;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.util.AmountUtil;
import com.jamplifier.investments.util.ChatInputManager;
import com.jamplifier.investments.util.GuiClickGuard;
import com.jamplifier.investments.util.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InvestmentsMenuListener implements Listener {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final Economy economy;
    private final ChatInputManager chatInputManager;

    public InvestmentsMenuListener(InvestmentsPlugin plugin,
                                   InvestmentManager investmentManager,
                                   EconomyHook economyHook) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        this.economy = economyHook.getEconomy();
        this.chatInputManager = plugin.getChatInputManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Object holder = event.getInventory().getHolder();

        if (holder instanceof InvestmentsMenu) {
            handleMainMenuClick(event, player);
        } 
    }

    // ===================== MAIN MENU =====================

    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            return; // click in player inventory
        }
        
        if (com.jamplifier.investments.util.GuiClickGuard.shouldBlock(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        InvestmentProfile profile = investmentManager.getProfile(uuid);

        // DELETE investment -> open confirm GUI instead of instant delete
        if (rawSlot == InvestmentsMenu.getDeleteSlot()) {
            if (profile.getInvestments().isEmpty()) {
                MessageUtils.send(player, "no-investments");
                return;
            }

            ConfirmDeleteMenu.openFor(player, profile);
            return;
        }

        // INFO -> chat input invest (supports pre-selected options)
        if (rawSlot == InvestmentsMenu.getInfoSlot()) {
            // Close GUI so they can immediately type in chat
            player.closeInventory();

            chatInputManager.await(player, (p, message) -> {
                BigDecimal amount = resolveAmountFromInput(message);
                if (amount == null) {
                    MessageUtils.send(p, "invalid-amount");
                    return;
                }

                handleInvest(p, amount);

                // Re-open GUI after a successful invest
                InvestmentsMenu.openFor(p, investmentManager.getProfile(p.getUniqueId()));
            });
            return;
        }

        // Auto-collect toggle slot (red/green glass)
        if (rawSlot == InvestmentsMenu.getAutocollectSlot()) {
            if (!player.hasPermission(plugin.getConfig().getString("autocollect.permission", "investments.autocollect"))) {
                MessageUtils.send(player, "no-permission");
                return;
            }

            boolean newState = !profile.isAutoCollect();
            profile.setAutoCollect(newState);
            investmentManager.saveProfile(profile);

            MessageUtils.send(player, newState ? "autocollect-enabled" : "autocollect-disabled");
            InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));
            return;
        }

        // COLLECT profits (left-click collect, right-click also toggle auto)
        if (rawSlot == InvestmentsMenu.getCollectSlot()) {
            // Right-click = toggle auto-collect
            if (event.getClick() == ClickType.RIGHT) {
                if (!player.hasPermission(plugin.getConfig().getString("autocollect.permission", "investments.autocollect"))) {
                    MessageUtils.send(player, "no-permission");
                    return;
                }

                boolean newState = !profile.isAutoCollect();
                profile.setAutoCollect(newState);
                investmentManager.saveProfile(profile);

                MessageUtils.send(player, newState ? "autocollect-enabled" : "autocollect-disabled");
                InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));
                return;
            }

         // Left-click = collect profits
            BigDecimal collected = investmentManager.collectProfit(player);
            if (collected.compareTo(BigDecimal.ZERO) <= 0) {
                MessageUtils.send(player, "no-profit");
                return;
            }

            economy.depositPlayer(player, collected.doubleValue());

            Map<String, String> ph = new HashMap<>();
            ph.put("amount", AmountUtil.formatShort(collected));
            MessageUtils.send(player, "profit-collected", ph);

            InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));

        }
    }

    // ===================== CONFIRM DELETE MENU =====================

    private void handleConfirmDeleteClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Confirm deletion
        if (rawSlot == ConfirmDeleteMenu.getYesSlot()) {
            investmentManager.deleteInvestments(player);
            MessageUtils.send(player, "investment-deleted");

            // Open main menu again (now empty)
            InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));
            return;
        }

        // Cancel -> go back to main menu
        if (rawSlot == ConfirmDeleteMenu.getNoSlot()) {
            InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));
        }
    }

    // ===================== UTIL / INVEST HANDLING =====================

    /** Resolve input from chat: either pre-selected option index, or raw numeric amount. */
    private BigDecimal resolveAmountFromInput(String input) {
        String trimmed = input.trim();

        // First: check pre-selected-investments.<input>
        ConfigurationSection pre = plugin.getConfig().getConfigurationSection("pre-selected-investments");
        if (pre != null && pre.isSet(trimmed)) {
            double val = pre.getDouble(trimmed, -1.0D);
            if (val > 0) {
                return BigDecimal.valueOf(val);
            }
        }

        // Fallback: parse as numeric amount (supports k/m/b etc. via AmountUtil)
        return AmountUtil.parseAmount(trimmed);
    }

    private void handleInvest(Player player, BigDecimal amount) {
        double bal = economy.getBalance(player);
        if (bal < amount.doubleValue()) {
            MessageUtils.send(player, "not-enough-money");
            return;
        }

        int max = investmentManager.getMaxInvestments(player);
        InvestmentProfile profile = investmentManager.getProfile(player.getUniqueId());
        if (max > 0 && profile.getInvestments().size() >= max) {
            Map<String, String> ph = new HashMap<>();
            ph.put("limit", String.valueOf(max));
            MessageUtils.send(player, "max-investments-reached", ph);
            return;
        }

        economy.withdrawPlayer(player, amount.doubleValue());
        investmentManager.addInvestment(player, amount);

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", AmountUtil.formatShort(amount));
        MessageUtils.send(player, "invest-added", ph);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GuiClickGuard.clear(event.getPlayer());
    }

}
