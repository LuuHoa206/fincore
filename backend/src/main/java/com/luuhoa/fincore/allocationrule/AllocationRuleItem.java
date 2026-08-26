package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.util.UUID;

import com.luuhoa.fincore.moneyjar.MoneyJar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "allocation_rule_items")
public class AllocationRuleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AllocationRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jar_id", nullable = false)
    private MoneyJar jar;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    protected AllocationRuleItem() {
    }

    public AllocationRuleItem(MoneyJar jar, BigDecimal percentage) {
        this.jar = jar;
        this.percentage = percentage;
    }

    void assignRule(AllocationRule rule) { this.rule = rule; }
    public MoneyJar getJar() { return jar; }
    public BigDecimal getPercentage() { return percentage; }
}
