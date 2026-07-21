package com.shop.simulator.dto;

public record SimulationStatus(
    boolean active,
    double totalRevenue,
    double currentCapital,
    int transactionCount,
    int restockCount
) {}
