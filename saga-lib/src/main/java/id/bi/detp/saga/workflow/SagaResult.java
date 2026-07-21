package id.bi.detp.saga.workflow;

public record SagaResult(String uetr, String status, boolean duplicate) {}
