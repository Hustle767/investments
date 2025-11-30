package com.jamplifier.investments.investment;

import com.jamplifier.investments.storage.InvestmentStorage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class InvestmentProfile {

    private final UUID owner;
    private final List<Investment> investments = new ArrayList<>();
    private boolean autoCollect;

    public InvestmentProfile(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public List<Investment> getInvestments() {
        return Collections.unmodifiableList(investments);
    }

    public boolean isAutoCollect() {
        return autoCollect;
    }

    public void setAutoCollect(boolean autoCollect) {
        this.autoCollect = autoCollect;
    }

    public void addInvestment(BigDecimal amount) {
        investments.add(new Investment(owner, amount, BigDecimal.ZERO));
    }

    public void deleteAllInvestments() {
        investments.clear();
    }

    public BigDecimal getTotalInvested() {
        BigDecimal total = BigDecimal.ZERO;
        for (Investment inv : investments) {
            total = total.add(inv.getInvested());
        }
        return total;
    }

    public BigDecimal getTotalProfit() {
        BigDecimal total = BigDecimal.ZERO;
        for (Investment inv : investments) {
            total = total.add(inv.getProfit());
        }
        return total;
    }

    /** Take ALL profit from all investments and reset them. */
    public BigDecimal collectAllProfit() {
        BigDecimal total = BigDecimal.ZERO;
        for (Investment inv : investments) {
            total = total.add(inv.takeProfit());
        }
        return total;
    }

    // --- Storage hooks (simple for now; full SQL/Mongo later) ---

    public void load(InvestmentStorage storage) {
        investments.clear();
        investments.addAll(storage.loadInvestments(owner));
        this.autoCollect = storage.loadAutoCollect(owner);
    }

    public void save(InvestmentStorage storage) {
        storage.saveProfile(owner, investments, autoCollect);
    }
}
