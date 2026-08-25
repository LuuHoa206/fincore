package com.luuhoa.fincore.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsVietnameseDongWithoutFractionalDigits() {
        Money money = Money.of(new BigDecimal("1000.6"), "VND");

        assertThat(money.amount()).isEqualByComparingTo("1001");
    }

    @Test
    void addsMoneyWithTheSameCurrency() {
        Money result = Money.of(new BigDecimal("120000"), "VND")
                .add(Money.of(new BigDecimal("30000"), "VND"));

        assertThat(result.amount()).isEqualByComparingTo("150000");
    }

    @Test
    void rejectsOperationsAcrossCurrencies() {
        Money vnd = Money.of(new BigDecimal("100000"), "VND");
        Money usd = Money.of(new BigDecimal("4"), "USD");

        assertThatThrownBy(() -> vnd.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Money currencies must match");
    }
}
