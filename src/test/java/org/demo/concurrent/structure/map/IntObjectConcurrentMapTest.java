package org.demo.concurrent.structure.map;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/**
 * @author: ryan_scy@126.com
 * @date: 2025-01-27
 * @time: 12:46
 * @desc:
 */
public class IntObjectConcurrentMapTest {

    @Test
    void putAndGetTest() {
        IntObjectConcurrentMap<Balance> intObjectConcurrentMap = new IntObjectConcurrentMap<>();
        intObjectConcurrentMap.put(0, Balance.builder().coinId(0).balance(BigDecimal.ONE).build());
        intObjectConcurrentMap.put(1, Balance.builder().coinId(1).balance(BigDecimal.TEN).build());
        intObjectConcurrentMap.put(2, Balance.builder().coinId(2).balance(BigDecimal.ZERO).build());

        intObjectConcurrentMap.get(1).update(BigDecimal.valueOf(2));
        intObjectConcurrentMap.get(2).update(BigDecimal.valueOf(4));

        intObjectConcurrentMap.forEach((k, v) -> {
            System.out.println("key:" + k + ",value:" + v);
        });

    }

    @Data
    @Builder
    public static class Balance implements ISequence {

        @Builder.Default
        private long sequence = 0L;
        private BigDecimal balance;
        private int coinId;

        public Balance update(BigDecimal balance) {
            this.balance = balance;
            return this;
        }

        @Override
        public void updateSequence(long sequence) {
            this.sequence = sequence;
        }
    }
}
