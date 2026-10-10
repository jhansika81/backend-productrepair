package com.example.productrepair.dto;

import com.example.productrepair.entity.TechnicianApplicationStatus;

import java.time.LocalDateTime;

public record TechnicianApplicationResponse(Long id, Long userId, String name, String email,
        String phone, String address, String serviceLocation, String skills, int yearsExperience,
        String qualifications, TechnicianApplicationStatus status, String rejectionReason,
        String documentName, LocalDateTime submittedAt, LocalDateTime approvedAt,
        LocalDateTime rejectedAt) {}
