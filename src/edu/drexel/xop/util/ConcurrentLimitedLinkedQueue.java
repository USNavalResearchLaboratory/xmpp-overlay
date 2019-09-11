package edu.drexel.xop.util;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Extends a CLQ to have a maximum number of elements
 *
 * @author di
 */
@SuppressWarnings("serial")
public class ConcurrentLimitedLinkedQueue<E> extends ConcurrentLinkedQueue<E> {

    // The limit size of the queue
    private int limit;

    public ConcurrentLimitedLinkedQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E o) {
        // Remove if the queue size is above the limit
        while (size() > limit) {
            super.remove();
        }
        // Add the object to be added
        return super.add(o);
    }
}
