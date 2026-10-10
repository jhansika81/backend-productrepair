package com.example.productrepair.dto;

public record RepairContactResponse(Long repairId, String customerName, String issue,
        String description, String serviceAddress, String preferredContactMethod,
        String phone, String email) {}
