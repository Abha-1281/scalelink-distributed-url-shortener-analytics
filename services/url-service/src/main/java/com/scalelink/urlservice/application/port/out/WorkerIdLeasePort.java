package com.scalelink.urlservice.application.port.out;

public interface WorkerIdLeasePort {

    long acquireWorkerId();

    void renewLease(long workerId);

    void releaseLease(long workerId);
}
