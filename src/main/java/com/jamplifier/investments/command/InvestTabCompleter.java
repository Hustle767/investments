package com.jamplifier.investments.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class InvestTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!command.getName().equalsIgnoreCase("invest")) {
            return Collections.emptyList();
        }

        // /invest <...>
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Player suggestions
            completions.add("1000");
            completions.add("10000");
            completions.add("50000");
            completions.add("notify");

            // Admin subcommands
            if (sender.hasPermission("investments.admin.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("investments.admin.delete")) {
                completions.add("delete");
            }
            if (sender.hasPermission("investments.admin.multiplier")) {
                completions.add("multiplier");
            }
            if (sender.hasPermission("investments.admin.view")) {
                completions.add("view");
            }
            if (sender.hasPermission("investments.admin.give")) {
                completions.add("give");
            }

            return partial(completions, args[0]);
        }

        // /invest delete <player>
        if (args.length == 2 &&
                args[0].equalsIgnoreCase("delete") &&
                sender.hasPermission("investments.admin.delete")) {

            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return partial(names, args[1]);
        }

        // /invest view <player>
        if (args.length == 2 &&
                args[0].equalsIgnoreCase("view") &&
                sender.hasPermission("investments.admin.view")) {

            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return partial(names, args[1]);
        }

        // /invest give <player> <amount>
        if (args[0].equalsIgnoreCase("give") &&
                sender.hasPermission("investments.admin.give")) {

            if (args.length == 2) {
                List<String> names = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return partial(names, args[1]);
            }

            if (args.length == 3) {
                return partial(Arrays.asList("10000", "50000", "100000"), args[2]);
            }
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
