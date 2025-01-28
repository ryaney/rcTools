package org.demo.concurrent.structure.map;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/**
 * @author: ryan_scy@126.com
 * @date: 2025-01-27
 * @time: 12:46
 * @desc:
 */
public class IntObjectConcurrentMapTest {

    IntObjectConcurrentMap<Balance> intObjectConcurrentMap = new IntObjectConcurrentMap<>();

    @Test
    void putAndGetTest() {
        IntObjectConcurrentMap<Balance> intObjectConcurrentMap = new IntObjectConcurrentMap<>();
        intObjectConcurrentMap.put(0, Balance.builder().coinId(0).balance(BigDecimal.ONE).build());
        intObjectConcurrentMap.put(1, Balance.builder().coinId(1).balance(BigDecimal.TEN).build());
        intObjectConcurrentMap.put(2, Balance.builder().coinId(2).balance(BigDecimal.ZERO).build());

        intObjectConcurrentMap.get(1).update(BigDecimal.valueOf(2));
        intObjectConcurrentMap.get(2).update(BigDecimal.valueOf(4));
        intObjectConcurrentMap.remove(1);

        intObjectConcurrentMap.forEach((k, v) -> {
            System.out.println("key:" + k + ",value:" + v);
        });

    }

    @Test
    void concurrentTest() throws InterruptedException {
        Random random = new Random();
        Runnable runnableWrite = () -> {
            while (true) {
                for (int i = 0; i < random.nextInt(0, 1000); i++) {
                    intObjectConcurrentMap.put(i, Balance.builder().coinId(i).balance(BigDecimal.valueOf(i)).build());
                }
                LockSupport.parkNanos(1000L);
            }
        };

        Runnable runnableRemove = () -> {
            while (true) {
                for (int i = 0; i < random.nextInt(0, 1000); i++) {
                    intObjectConcurrentMap.remove(i);
                }
                LockSupport.parkNanos(1000L);
            }
        };

        Runnable runnableRead = () -> {
            while (true) {
                try {
                    intObjectConcurrentMap.forEach((k, v) -> {
                        LockSupport.parkNanos(100L);
                        System.out.println("key:" + k + ", value:" + v);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LockSupport.parkNanos(1000L);
            }
        };

        Thread write = new Thread(runnableWrite);
        Thread read = new Thread(runnableRead);
        Thread remove = new Thread(runnableRemove);
        write.start();
        read.start();
        remove.start();
        write.join();
        read.join();
        remove.join();
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
