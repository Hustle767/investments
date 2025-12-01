package com.jamplifier.investments;

import com.jamplifier.investments.command.InvestAdminCommand;
import com.jamplifier.investments.command.InvestPlayerCommand;
import com.jamplifier.investments.command.InvestTabCompleter;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.gui.ConfirmDeleteMenu;
import com.jamplifier.investments.gui.ConfirmDeleteMenuListener;
import com.jamplifier.investments.gui.InvestmentsMenu;
import com.jamplifier.investments.gui.InvestmentsMenuListener;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.placeholder.InvestmentsPlaceholderExpansion;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.storage.InvestmentStorage;
import com.jamplifier.investments.storage.mongo.MongoInvestmentStorage;
import com.jamplifier.investments.storage.sql.SqlInvestmentStorage;
import com.jamplifier.investments.storage.sql.SqliteInvestmentStorage;
import com.jamplifier.investments.util.ChatInputManager;
import com.jamplifier.investments.util.ConfigKeys;
import com.jamplifier.investments.util.FoliaSchedulerUtil;
import com.jamplifier.investments.util.MessageUtils;
import net.milkbowl.vault.economy.Economy;


import net.milkbowl.vault.economy.Economy;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class InvestmentsPlugin extends JavaPlugin {

    private static InvestmentsPlugin instance;

    private EconomyHook economyHook;
    private InvestmentStorage storage;
    private InvestmentManager investmentManager;
    private ChatInputManager chatInputManager;
    private InterestService interestService;
    

    public static InvestmentsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MessageUtils.init(this);
        FoliaSchedulerUtil.init(this);
        InvestmentsMenu.init(this);
        ConfirmDeleteMenu.init(this);

        this.economyHook = new EconomyHook(this);
        if (!economyHook.setupEconomy()) {
            getLogger().severe("Vault economy not found. Disabling Investments plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.storage = createStorage();
        this.storage.init();

        this.investmentManager = new InvestmentManager(this, storage);

        // Interest ticking
        this.interestService = new InterestService(this, investmentManager);
        interestService.start();

        this.chatInputManager = new ChatInputManager();
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        getServer().getPluginManager().registerEvents(
                new InvestmentsMenuListener(this, investmentManager, economyHook), this
        );
        getServer().getPluginManager().registerEvents(
                new ConfirmDeleteMenuListener(investmentManager), this
        );

        
        InvestAdminCommand adminCommand = new InvestAdminCommand(this, investmentManager, interestService);
        InvestPlayerCommand playerCommand = new InvestPlayerCommand(
                this, investmentManager, economyHook, interestService, adminCommand
        );
        InvestTabCompleter tabCompleter = new InvestTabCompleter();

        var cmd = getCommand("invest");
        if (cmd != null) {
            cmd.setExecutor(playerCommand);      // routes to adminCommand internally when needed
            cmd.setTabCompleter(tabCompleter);
        }
        
     // PlaceholderAPI hook
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null && papi.isEnabled()) {
            new InvestmentsPlaceholderExpansion(this, investmentManager, interestService).register();
            getLogger().info("[Investments] Hooked into PlaceholderAPI.");
        } else {
            getLogger().info("[Investments] PlaceholderAPI not found; PAPI placeholders disabled.");
        }
        
    }


    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }
    
    public void reloadAll() {
        // core config.yml
        reloadConfig();

        // messages.yml
        MessageUtils.reload();

        // gui.yml
        InvestmentsMenu.reloadConfig();
        ConfirmDeleteMenu.reloadConfig();

        // max-invest-permissions
        if (investmentManager != null) {
            investmentManager.reloadPermissions();
        }

        // interest (rate + interval)
        if (interestService != null) {
            interestService.reloadFromConfig();
        }
    }

    private InvestmentStorage createStorage() {
        String type = getConfig().getString("storage-type", "SQLITE").toUpperCase(Locale.ROOT);

        switch (type) {
            case "MYSQL":
                getLogger().info("[Investments] Using MySQL storage backend.");
                return new SqlInvestmentStorage(this);

            case "SQLITE":
                getLogger().info("[Investments] Using SQLite storage backend.");
                return new SqliteInvestmentStorage(this);

            case "MONGODB":
                getLogger().info("[Investments] Using MongoDB storage backend.");
                return new MongoInvestmentStorage(this);

            default:
                getLogger().warning("[Investments] Unknown storage-type '" + type + "', falling back to SQLite.");
                return new SqliteInvestmentStorage(this);
        }
    }


    public EconomyHook getEconomyHook() {
        return economyHook;
    }

    public InvestmentManager getInvestmentManager() {
        return investmentManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }
    
    public InterestService getInterestService() {
        return interestService;
    }
    public Economy getEconomy() {
        return (economyHook != null) ? economyHook.getEconomy() : null;
    }
}
