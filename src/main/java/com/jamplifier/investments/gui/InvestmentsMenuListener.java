package com.jamplifier.investments.gui;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.util.ChatInputManager;
import com.jamplifier.investments.util.MessageUtils;
import com.jamplifier.investments.util.AmountUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

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
        if (!(event.getInventory().getHolder() instanceof InvestmentsMenu menu)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            return; // player inventory
        }

        UUID uuid = player.getUniqueId();
        InvestmentProfile profile = investmentManager.getProfile(uuid);

        // DELETE investment
        if (rawSlot == InvestmentsMenu.getDeleteSlot()) {
            if (profile.getInvestments().isEmpty()) {
                MessageUtils.send(player, "no-investments");
                return;
            }
            investmentManager.deleteInvestments(player);
            MessageUtils.send(player, "investment-deleted");
            InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));
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

        // COLLECT profits (left-click collect, right-click also toggle auto if you want)
        if (rawSlot == InvestmentsMenu.getCollectSlot()) {
            // Right-click = toggle auto-collect (optional; keep or remove)
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
            ph.put("amount", collected.toPlainString());
            MessageUtils.send(player, "profit-collected", ph);

            InvestmentsMenu.openFor(player, investmentManager.getProfile(uuid));
        }
    }

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

        // Fallback: parse as numeric amount
        return AmountUtil.parseAmount(trimmed);
    }

    private BigDecimal parseAmount(String input) {
        try {
            BigDecimal bd = new BigDecimal(input);
            if (bd.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return bd;
        } catch (NumberFormatException e) {
            return null;
        }
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
        ph.put("amount", amount.toPlainString());
        MessageUtils.send(player, "invest-added", ph);
    }
}
