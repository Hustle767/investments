package com.jamplifier.investments.command;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.gui.InvestmentsMenu;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.util.AmountUtil;
import com.jamplifier.investments.util.ConfigKeys;
import com.jamplifier.investments.util.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class InvestPlayerCommand implements CommandExecutor {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final Economy economy;
    private final InterestService interestService;
    private final InvestAdminCommand adminCommand;

    public InvestPlayerCommand(InvestmentsPlugin plugin,
                               InvestmentManager investmentManager,
                               EconomyHook economyHook,
                               InterestService interestService,
                               InvestAdminCommand adminCommand) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        this.economy = economyHook.getEconomy();
        this.interestService = interestService;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Route admin subcommands first if the first arg matches one
        if (args.length > 0 && adminCommand.isAdminSubcommand(args[0])) {
            return adminCommand.handleAdminCommand(sender, args);
        }

        // Everything below here is strictly player-facing
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("investments.use")) {
            MessageUtils.send(player, "no-permission");
            return true;
        }

        // /invest notify -> toggle interest notifications
        if (args.length == 1 && args[0].equalsIgnoreCase("notify")) {
            boolean nowEnabled = interestService.toggleNotify(player.getUniqueId());
            MessageUtils.send(player, nowEnabled ? "notify-enabled" : "notify-disabled");
            return true;
        }

        // /invest -> open GUI
        if (args.length == 0) {
            InvestmentProfile profile = investmentManager.getProfile(player.getUniqueId());
            InvestmentsMenu.openFor(player, profile);
            return true;
        }

        // /invest <amount>
        BigDecimal amount = AmountUtil.parseAmount(args[0]);
        if (amount == null) {
            MessageUtils.send(player, "invalid-amount");
            return true;
        }

        handleInvest(player, amount);
        return true;
    }

    private void handleInvest(Player player, BigDecimal amount) {
        // Enforce minimum investment from config
        double minAmountDouble = plugin.getConfig().getDouble(ConfigKeys.MIN_INVEST_AMOUNT, 10000.0D);
        BigDecimal minAmount = BigDecimal.valueOf(minAmountDouble);

        if (amount.compareTo(minAmount) < 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("amount", amount.toPlainString());
            ph.put("min", minAmount.toPlainString());
            MessageUtils.send(player, "error-min-invest-amount", ph);
            return;
        }

        // Balance + slot checks
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
