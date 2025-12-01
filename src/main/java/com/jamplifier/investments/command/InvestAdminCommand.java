package com.jamplifier.investments.command;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.util.AmountUtil;
import com.jamplifier.investments.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InvestAdminCommand {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final InterestService interestService;

    public InvestAdminCommand(InvestmentsPlugin plugin,
                              InvestmentManager investmentManager,
                              InterestService interestService) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        this.interestService = interestService;
    }

    public boolean isAdminSubcommand(String firstArg) {
        if (firstArg == null) return false;
        String sub = firstArg.toLowerCase(Locale.ROOT);
        return sub.equals("reload")
                || sub.equals("delete")
                || sub.equals("multiplier")
                || sub.equals("view")
                || sub.equals("give");
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return false;

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "delete":
                return handleDelete(sender, args);
            case "multiplier":
                return handleMultiplier(sender, args);
            case "view":
                return handleView(sender, args);
            case "give":
                return handleGive(sender, args);
            default:
                return false;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("investments.admin.reload")) {
            MessageUtils.send(sender, "no-permission");
            return true;
        }
        plugin.reloadAll();
        MessageUtils.send(sender, "admin-reload");
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("investments.admin.delete")) {
            MessageUtils.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /invest delete <player>");
            return true;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null || target.getName() == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", args[1]);
            MessageUtils.send(sender, "admin-player-not-found", ph);
            return true;
        }

        InvestmentProfile profile = investmentManager.getProfile(target.getUniqueId());
        if (profile.getInvestments().isEmpty()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            MessageUtils.send(sender, "admin-delete-none", ph);
            return true;
        }

        profile.deleteAllInvestments();
        investmentManager.saveProfile(profile);

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.getName());
        MessageUtils.send(sender, "admin-delete-success", ph);
        return true;
    }

    private boolean handleMultiplier(CommandSender sender, String[] args) {
        if (!sender.hasPermission("investments.admin.multiplier")) {
            MessageUtils.send(sender, "no-permission");
            return true;
        }

        if (args.length < 4) {
            MessageUtils.send(sender, "admin-multiplier-invalid");
            return true;
        }

        String targetArg = args[1];
        String multArg = args[2];
        String minutesArg = args[3];

        BigDecimal multiplier;
        int minutes;

        try {
            multiplier = new BigDecimal(multArg);
            if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
                MessageUtils.send(sender, "admin-multiplier-invalid");
                return true;
            }
        } catch (NumberFormatException e) {
            MessageUtils.send(sender, "admin-multiplier-invalid");
            return true;
        }

        try {
            minutes = Integer.parseInt(minutesArg);
            if (minutes <= 0) {
                MessageUtils.send(sender, "admin-multiplier-invalid");
                return true;
            }
        } catch (NumberFormatException e) {
            MessageUtils.send(sender, "admin-multiplier-invalid");
            return true;
        }

        if (targetArg.equalsIgnoreCase("all")) {
            interestService.setGlobalMultiplier(multiplier, minutes);
            Map<String, String> ph = new HashMap<>();
            ph.put("multiplier", multiplier.toPlainString());
            ph.put("minutes", String.valueOf(minutes));
            MessageUtils.send(sender, "admin-multiplier-set-global", ph);
        } else {
            OfflinePlayer target = resolvePlayer(targetArg);
            if (target == null || target.getUniqueId() == null || target.getName() == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("player", targetArg);
                MessageUtils.send(sender, "admin-player-not-found", ph);
                return true;
            }

            interestService.setPlayerMultiplier(target.getUniqueId(), multiplier, minutes);

            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            ph.put("multiplier", multiplier.toPlainString());
            ph.put("minutes", String.valueOf(minutes));
            MessageUtils.send(sender, "admin-multiplier-set-player", ph);
        }

        return true;
    }

    // /invest view <player>
    private boolean handleView(CommandSender sender, String[] args) {
        if (!sender.hasPermission("investments.admin.view")) {
            MessageUtils.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /invest view <player>");
            return true;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null || target.getName() == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", args[1]);
            MessageUtils.send(sender, "admin-player-not-found", ph);
            return true;
        }

        InvestmentProfile profile = investmentManager.getProfile(target.getUniqueId());

        String autoStatus = profile.isAutoCollect() ? "Enabled" : "Disabled";

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.getName());
        ph.put("total_invested", profile.getTotalInvested().toPlainString());
        ph.put("total_profit", profile.getTotalProfit().toPlainString());
        ph.put("count", String.valueOf(profile.getInvestments().size()));
        ph.put("autocollect", autoStatus);

        MessageUtils.send(sender, "admin-view-profile", ph);
        return true;
    }

    // /invest give <player> <amount>
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("investments.admin.give")) {
            MessageUtils.send(sender, "no-permission");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("Usage: /invest give <player> <amount>");
            return true;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null || target.getUniqueId() == null || target.getName() == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", args[1]);
            MessageUtils.send(sender, "admin-player-not-found", ph);
            return true;
        }

        BigDecimal amount = AmountUtil.parseAmount(args[2]);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            MessageUtils.send(sender, "invalid-amount");
            return true;
        }

        InvestmentProfile profile = investmentManager.getProfile(target.getUniqueId());
        profile.addInvestment(amount);
        investmentManager.saveProfile(profile);

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.getName());
        ph.put("amount", amount.toPlainString());
        ph.put("total_invested", profile.getTotalInvested().toPlainString());

        MessageUtils.send(sender, "admin-give-success", ph);
        return true;
    }

    private OfflinePlayer resolvePlayer(String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target != null) return target;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        return null;
    }
}
