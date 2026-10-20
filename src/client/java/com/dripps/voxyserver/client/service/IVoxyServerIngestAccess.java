package com.dripps.voxyserver.client.service;

public interface IVoxyServerIngestAccess {
    RemoteIngestService voxyserver$getRemoteIngestService();
    boolean voxyserver$isUsingRemoteIngest();

    void voxyserver$setUsingRemoteIngest(boolean remoteIngest);
}