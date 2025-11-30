package com.jamplifier.investments.storage.mongo;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.Investment;
import com.jamplifier.investments.storage.InvestmentStorage;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Mongo storage stub for now – main storage is SQL.
 * We can flesh this out later if you actually want to use Mongo.
 */
public class MongoInvestmentStorage implements InvestmentStorage {

    private final InvestmentsPlugin plugin;

    public MongoInvestmentStorage(InvestmentsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        plugin.getLogger().warning("[Investments] MongoDB storage selected, but implementation is stubbed. " +
                "Data will not persist across restarts yet.");
    }

    @Override
    public List<Investment> loadInvestments(UUID playerId) {
        return Collections.emptyList();
    }

    @Override
    public boolean loadAutoCollect(UUID playerId) {
        return false;
    }

    @Override
    public void saveProfile(UUID playerId, List<Investment> investments, boolean autoCollect) {
        // no-op for now
    }

    @Override
    public void deleteInvestments(UUID playerId) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
