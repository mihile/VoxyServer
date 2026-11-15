package com.dripps.voxyserver.network;

import java.util.ArrayDeque;

/** Serializes network callbacks while retaining the identity of their connection. */
public final class ConnectionInbox<H> {
    public record Token<H>(long generation, H handler) {}
    public record Event<H>(Token<H> token, LodPayload payload) {}
    private final ArrayDeque<Event<H>> queue = new ArrayDeque<>();
    private Token<H> active;
    private long generation;
    private int batches;
    private long bytes;

    public synchronized Token<H> begin(H handler) {
        active = new Token<>(++generation, handler);
        clear();
        return active;
    }

    public synchronized Token<H> tokenFor(H handler) {
        return active != null && active.handler() == handler ? active : null;
    }

    public synchronized Token<H> current() { return active; }
    public synchronized boolean isCurrent(Token<H> token) { return token != null && token == active; }

    public synchronized boolean offer(Token<H> token, LodPayload payload) {
        if (!isCurrent(token) || queue.size() >= 256) return false;
        if (payload instanceof PreSerializedLodPayload bulk) {
            if (batches >= 64 || bytes + bulk.data().length > 64L * 1024 * 1024) return false;
            batches++; bytes += bulk.data().length;
        }
        queue.addLast(new Event<>(token, payload));
        return true;
    }

    public synchronized Event<H> poll(Token<H> token) {
        if (!isCurrent(token)) return null;
        Event<H> event = queue.pollFirst();
        if (event != null && event.payload() instanceof PreSerializedLodPayload bulk) {
            batches--; bytes -= bulk.data().length;
        }
        return event;
    }

    public synchronized boolean end(H handler) {
        if (tokenFor(handler) == null) return false;
        active = null;
        generation++;
        clear();
        return true;
    }

    public synchronized int size() { return queue.size(); }
    private void clear() { queue.clear(); batches = 0; bytes = 0; }
}
