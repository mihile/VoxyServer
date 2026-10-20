package com.dripps.voxyserver.network;

import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ConnectionInboxTest {
    @Test public void protocolSessionAndBulkKeepWireOrder() {
        var inbox = new ConnectionInbox<Object>();
        var token = inbox.begin(new Object());
        var protocol = new LODProtocolPayload(2);
        var bulk = new PreSerializedLodPayload(1, 1, 1, new byte[8]);
        assertTrue(inbox.offer(token, protocol)); assertTrue(inbox.offer(token, bulk));
        assertSame(protocol, inbox.poll(token).payload()); assertSame(bulk, inbox.poll(token).payload());
        assertNull(inbox.poll(token));
    }
    @Test public void reconnectRejectsOldEventsAndCompletions() {
        var inbox = new ConnectionInbox<Object>(); Object old = new Object(), next = new Object();
        var before = inbox.begin(old); inbox.offer(before, new LODProtocolPayload(2));
        var after = inbox.begin(next);
        assertFalse(inbox.isCurrent(before)); assertFalse(inbox.offer(before, new LODProtocolPayload(2)));
        assertNull(inbox.poll(before)); assertNull(inbox.poll(after));
        assertFalse(inbox.end(old)); assertTrue(inbox.isCurrent(after));
    }
    @Test public void sameHandlerReuseStillGetsNewGeneration() {
        var inbox = new ConnectionInbox<Object>(); Object handler = new Object();
        var first = inbox.begin(handler); inbox.end(handler); var second = inbox.begin(handler);
        assertNotEquals(first.generation(), second.generation()); assertFalse(inbox.isCurrent(first));
    }
    @Test public void waitingQueueIsBoundedAndResetsOnDisconnect() {
        var inbox = new ConnectionInbox<Object>(); Object handler = new Object(); var token = inbox.begin(handler);
        for(int i=0;i<64;i++) assertTrue(inbox.offer(token,new PreSerializedLodPayload(1,i,1,new byte[1])));
        assertFalse(inbox.offer(token,new PreSerializedLodPayload(1,65,1,new byte[1])));
        assertTrue(inbox.offer(token,new LODProtocolPayload(2))); // Control packets can still invalidate a session.
        assertTrue(inbox.end(handler)); assertEquals(0,inbox.size());
        var next=inbox.begin(new Object());assertTrue(inbox.offer(next,new PreSerializedLodPayload(1,1,1,new byte[1])));
    }
    @Test public void delayedNetworkThreadCannotPollNewConnection() throws Exception {
        var inbox = new ConnectionInbox<Object>(); var old = inbox.begin(new Object());
        var executor=Executors.newSingleThreadExecutor();var gate=new CountDownLatch(1);
        try {
            var result=executor.submit(() -> {gate.await();return inbox.offer(old,new LODProtocolPayload(2));});
            var next=inbox.begin(new Object());gate.countDown();assertFalse(result.get());assertTrue(inbox.isCurrent(next));
        } finally {executor.shutdownNow();}
    }
    @Test public void cancellationBeforeLoginDoesNotResurrectHandler() {
        var inbox = new ConnectionInbox<Object>(); Object handler = new Object();
        var initializing = inbox.begin(handler);
        inbox.end(inbox.current().handler());
        assertNull(inbox.tokenFor(handler));
        assertFalse(inbox.offer(initializing, new LODProtocolPayload(2)));
        var replacement = inbox.begin(new Object());
        assertNull(inbox.tokenFor(handler)); assertTrue(inbox.isCurrent(replacement));
    }
}
