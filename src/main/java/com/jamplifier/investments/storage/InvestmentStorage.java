package com.jamplifier.investments.storage;

import com.jamplifier.investments.investment.Investment;

import java.util.List;
import java.util.UUID;

public interface InvestmentStorage {

    /** Setup connections / tables / collections. */
    void init();

    /** Load all investments for a player. */
    List<Investment> loadInvestments(UUID playerId);

    /** Load whether this player has auto-collect enabled. */
    boolean loadAutoCollect(UUID playerId);

    /**
     * Save the entire profile for a player.
     * Implementations should replace all existing data for that player.
     */
    void saveProfile(UUID playerId, List<Investment> investments, boolean autoCollect);

    /** Delete all investments for a player (and optionally profile info). */
    void deleteInvestments(UUID playerId);

    /** Close DB connections. */
    void close();
}
