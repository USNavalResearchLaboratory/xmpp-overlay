package edu.drexel.transportengine.components.clientmanager;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BoundedConcurrentLinkedQueue<T> extends ConcurrentLinkedQueue<T> {
    private int bound;

    public BoundedConcurrentLinkedQueue(int bound) {
        this.bound = bound;
    }

    @Override
    public boolean add(T obj) {
        while (size() > bound) {
            remove();
        }

        if (!contains(obj)) {
            return super.add(obj);
        }
        return false;
    }
}
