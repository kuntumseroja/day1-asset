package id.bi.detp.saga.workflow;

public record IssuanceRequest(String uetr, long amount, String participantId) {}
