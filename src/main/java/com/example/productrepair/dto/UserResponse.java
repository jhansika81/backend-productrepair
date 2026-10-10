package com.example.productrepair.dto;

import com.example.productrepair.entity.Role;
import com.example.productrepair.entity.TechnicianApplicationStatus;

public record UserResponse(Long id, String name, String email, String phone, Role role,
                           TechnicianApplicationStatus applicationStatus, String rejectionReason) {}
