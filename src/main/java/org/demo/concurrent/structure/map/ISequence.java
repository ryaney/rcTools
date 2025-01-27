package org.demo.concurrent.structure.map;

/**
 * @author: ryan_scy@126.com
 * @date: 2025-01-27
 * @time: 12:12
 * @desc:
 */
public interface ISequence {
    long getSequence();

    void updateSequence(long sequence);
}
