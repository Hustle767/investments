package com.jamplifier.investments;

import com.jamplifier.investments.command.InvestCommand;
import com.jamplifier.investments.economy.EconomyHook;
import com.jamplifier.investments.gui.InvestmentsMenu;
import com.jamplifier.investments.gui.InvestmentsMenuListener;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.storage.InvestmentStorage;
import com.jamplifier.investments.storage.mongo.MongoInvestmentStorage;
import com.jamplifier.investments.storage.sql.SqlInvestmentStorage;
import com.jamplifier.investments.storage.sql.SqliteInvestmentStorage;
import com.jamplifier.investments.util.ChatInputManager;
import com.jamplifier.investments.util.ConfigKeys;
import com.jamplifier.investments.util.FoliaSchedulerUtil;
import com.jamplifier.investments.util.MessageUtils;

import org.bukkit.command.PluginCommand;
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
        saveResource("gui.yml", false);
        saveResource("messages.yml", false);

        MessageUtils.init(this);
        FoliaSchedulerUtil.init(this);
        InvestmentsMenu.init(this);

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

        InvestCommand investCommand = new InvestCommand(this, investmentManager, economyHook);
        PluginCommand cmd = getCommand("invest");
        if (cmd != null) {
            cmd.setExecutor(investCommand);
            cmd.setTabCompleter(investCommand);
        }

    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }
    
    public void reloadAll() {
        reloadConfig();
        MessageUtils.reload();
        InvestmentsMenu.reloadConfig();
        interestService.reloadFromConfig();
    }

    private InvestmentStorage createStorage() {
        String type = getConfig().getString(ConfigKeys.STORAGE_TYPE, "SQLITE").toUpperCase();

        switch (type) {
            case "MYSQL":
                return new SqlInvestmentStorage(this);
            case "MONGODB":
                return new MongoInvestmentStorage(this);
            case "SQLITE":
            default:
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
}
