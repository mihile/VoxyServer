package com.dripps.voxyserver.util;

import me.cortex.voxy.common.voxelization.VoxelizedSection;

/** One scratch section per ingest task, released even when that task fails. */
public final class RemoteIngestScratch implements AutoCloseable {
    private static final ThreadLocal<RemoteIngestScratch> CURRENT=new ThreadLocal<>();
    private final RemoteIngestScratch previous;
    private VoxelizedSection section;
    private final java.util.ArrayList<short[]> borrowed=new java.util.ArrayList<>();
    private static final java.util.ArrayDeque<short[]> FREE=new java.util.ArrayDeque<>();
    private static final int POOLED_LENGTH=32768, MAX_FREE=32;
    private boolean closed;
    private RemoteIngestScratch(){previous=CURRENT.get();CURRENT.set(this);}
    public static RemoteIngestScratch begin(){return new RemoteIngestScratch();}
    public static VoxelizedSection acquire(){return acquire(VoxelizedSection::createEmpty);}
    public static VoxelizedSection acquire(java.util.function.Supplier<VoxelizedSection> original){
        RemoteIngestScratch scope=CURRENT.get();
        if(scope==null)return original.get();
        if(scope.section==null)scope.section=original.get();
        else scope.section.zero();
        return scope.section;
    }
    public static boolean active(){return CURRENT.get()!=null;}
    public static short[] indices(int size){
        RemoteIngestScratch scope=CURRENT.get();
        if(scope==null || size!=POOLED_LENGTH)return new short[size];
        short[] data; synchronized(FREE){data=FREE.pollFirst();}
        if(data==null)data=new short[size];
        scope.borrowed.add(data); return data;
    }
    public void close(){
        if(closed)return;
        if(CURRENT.get()!=this)throw new IllegalStateException("Ingest scope closed out of order");
        closed=true;section=null;
        synchronized(FREE){for(short[] data:borrowed)if(FREE.size()<MAX_FREE)FREE.addFirst(data);}
        borrowed.clear();
        if(previous==null)CURRENT.remove();else CURRENT.set(previous);
    }
}
