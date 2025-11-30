package com.jamplifier.investments.command;

import com.jamplifier.investments.util.AmountUtil;
import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.gui.InvestmentsMenu;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.util.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class InvestCommand implements CommandExecutor, TabCompleter {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final Economy economy;
    private final InterestService interestService;

    public InvestCommand(InvestmentsPlugin plugin,
                         InvestmentManager investmentManager,
                         EconomyHook economyHook) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        this.economy = economyHook.getEconomy();
        this.interestService = plugin.getInterestService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Admin subcommands: reload, delete, multiplier ...
        if (args.length > 0 && !(sender instanceof Player && isNumeric(args[0]))) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "reload":
                    if (!sender.hasPermission("investments.admin.reload")) {
                        MessageUtils.send(sender, "no-permission");
                        return true;
                    }
                    plugin.reloadAll();
                    MessageUtils.send(sender, "admin-reload");
                    return true;

                case "delete":
                    if (!sender.hasPermission("investments.admin.delete")) {
                        MessageUtils.send(sender, "no-permission");
                        return true;
                    }
                    handleAdminDelete(sender, args);
                    return true;

                case "multiplier":
                    if (!sender.hasPermission("investments.admin.multiplier")) {
                        MessageUtils.send(sender, "no-permission");
                        return true;
                    }
                    handleAdminMultiplier(sender, args);
                    return true;
                    
                case "notify":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }
                    Player p = (Player) sender;
                    boolean nowEnabled = interestService.toggleNotify(p.getUniqueId());
                    MessageUtils.send(p, nowEnabled ? "notify-enabled" : "notify-disabled");
                    return true;
             
            }
        }

        // Player usage
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("investments.use")) {
            MessageUtils.send(player, "no-permission");
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

    private void handleAdminDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /invest delete <player>");
            return;
        }

        String name = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            // fallback: try exact match among online players
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                target = online;
            }
        }

        if (target == null || (target.getName() == null)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", name);
            MessageUtils.send(sender, "admin-player-not-found", ph);
            return;
        }

        InvestmentProfile profile = investmentManager.getProfile(target.getUniqueId());
        if (profile.getInvestments().isEmpty()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            MessageUtils.send(sender, "admin-delete-none", ph);
            return;
        }

        profile.deleteAllInvestments();
        investmentManager.saveProfile(profile);

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.getName());
        MessageUtils.send(sender, "admin-delete-success", ph);
    }

    private void handleAdminMultiplier(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtils.send(sender, "admin-multiplier-invalid");
            return;
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
                return;
            }
        } catch (NumberFormatException e) {
            MessageUtils.send(sender, "admin-multiplier-invalid");
            return;
        }

        try {
            minutes = Integer.parseInt(minutesArg);
            if (minutes <= 0) {
                MessageUtils.send(sender, "admin-multiplier-invalid");
                return;
            }
        } catch (NumberFormatException e) {
            MessageUtils.send(sender, "admin-multiplier-invalid");
            return;
        }

        if (targetArg.equalsIgnoreCase("all")) {
            interestService.setGlobalMultiplier(multiplier, minutes);
            Map<String, String> ph = new HashMap<>();
            ph.put("multiplier", multiplier.toPlainString());
            ph.put("minutes", String.valueOf(minutes));
            MessageUtils.send(sender, "admin-multiplier-set-global", ph);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetArg);
            if (target == null) {
                Player online = Bukkit.getPlayerExact(targetArg);
                if (online != null) target = online;
            }

            if (target == null || target.getUniqueId() == null || target.getName() == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("player", targetArg);
                MessageUtils.send(sender, "admin-player-not-found", ph);
                return;
            }

            interestService.setPlayerMultiplier(target.getUniqueId(), multiplier, minutes);

            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            ph.put("multiplier", multiplier.toPlainString());
            ph.put("minutes", String.valueOf(minutes));
            MessageUtils.send(sender, "admin-multiplier-set-player", ph);
        }
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

    private boolean isNumeric(String s) {
        try {
            new BigDecimal(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ----- Tab completion -----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!command.getName().equalsIgnoreCase("invest")) return Collections.emptyList();

        // /invest <...>
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // default amounts for players
            completions.add("1000");
            completions.add("10000");
            completions.add("50000");

            // admin options
            if (sender.hasPermission("investments.admin.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("investments.admin.delete")) {
                completions.add("delete");
            }
            if (sender.hasPermission("investments.admin.multiplier")) {
                completions.add("multiplier");
            }
            
            completions.add("notify");

            return partial(completions, args[0]);
        }

        // /invest delete <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("delete") &&
                sender.hasPermission("investments.admin.delete")) {
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return partial(names, args[1]);
        }

        // /invest multiplier <player|all> <multiplier> <minutes>
        if (args[0].equalsIgnoreCase("multiplier") &&
                sender.hasPermission("investments.admin.multiplier")) {

            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList()));
                return partial(options, args[1]);
            }

            if (args.length == 3) {
                return partial(Arrays.asList("2", "1.5", "3"), args[2]);
            }

            if (args.length == 4) {
                return partial(Arrays.asList("5", "10", "30"), args[3]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> partial(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
