package com.dripps.voxyserver.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemoteIngestScratchTest {
 @Test public void scopedArraysHaveExclusiveOwnershipAndReturnAfterException() {
  short[] a,b;
  try(var scope=RemoteIngestScratch.begin()) {
   a=RemoteIngestScratch.indices(32768);b=RemoteIngestScratch.indices(32768);
   assertNotSame(a,b);a[0]=23;assertEquals(0,b[0]);
   try(var nested=RemoteIngestScratch.begin()){assertNotSame(a,RemoteIngestScratch.indices(32768));}
   assertTrue(RemoteIngestScratch.active());
  }
  assertFalse(RemoteIngestScratch.active());
  assertNotSame(RemoteIngestScratch.indices(32768),RemoteIngestScratch.indices(32768));
  try {try(var scope=RemoteIngestScratch.begin()){RemoteIngestScratch.indices(32768);throw new IllegalArgumentException();}}
  catch(IllegalArgumentException expected){}
  assertFalse(RemoteIngestScratch.active());
 }
 @Test public void voxelReuseClearsAllMipAndCountData() {
  try(var scope=RemoteIngestScratch.begin()) {
   var a=RemoteIngestScratch.acquire();java.util.Arrays.fill(a.section,71);a.lvl0NonAirCount=10;
   var b=RemoteIngestScratch.acquire();assertSame(a,b);assertEquals(0,b.lvl0NonAirCount);
   for(long value:b.section)assertEquals(0L,value);
  }
 }
}
