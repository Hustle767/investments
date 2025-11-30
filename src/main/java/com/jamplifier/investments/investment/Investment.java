package com.jamplifier.investments.investment;

import java.math.BigDecimal;
import java.util.UUID;

public class Investment {

    private final UUID owner;
    private BigDecimal invested;
    private BigDecimal profit;

    public Investment(UUID owner, BigDecimal invested, BigDecimal profit) {
        this.owner = owner;
        this.invested = invested == null ? BigDecimal.ZERO : invested;
        this.profit = profit == null ? BigDecimal.ZERO : profit;
    }

    public UUID getOwner() {
        return owner;
    }

    public BigDecimal getInvested() {
        return invested;
    }

    public void addInvested(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        this.invested = this.invested.add(amount);
    }

    public BigDecimal getProfit() {
        return profit;
    }

    public void addProfit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        this.profit = this.profit.add(amount);
    }

    /**
     * Take all current profit and reset it to zero.
     */
    public BigDecimal takeProfit() {
        BigDecimal taken = this.profit;
        this.profit = BigDecimal.ZERO;
        return taken;
    }
}
