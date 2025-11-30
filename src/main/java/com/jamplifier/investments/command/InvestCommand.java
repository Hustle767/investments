package com.jamplifier.investments.command;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.gui.InvestmentsMenu;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.util.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class InvestCommand implements CommandExecutor {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final Economy economy;

    public InvestCommand(InvestmentsPlugin plugin,
                         InvestmentManager investmentManager,
                         EconomyHook economyHook) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        this.economy = economyHook.getEconomy();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("investments.use")) {
            MessageUtils.send(player, "no-permission");
            return true;
        }

        // /invest → open GUI
        if (args.length == 0) {
            InvestmentProfile profile = investmentManager.getProfile(player.getUniqueId());
            InvestmentsMenu.openFor(player, profile);
            return true;
        }

        // /invest <amount>
        BigDecimal amount = parseAmount(args[0]);
        if (amount == null) {
            MessageUtils.send(player, "invalid-amount");
            return true;
        }

        handleInvest(player, amount);
        return true;
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
