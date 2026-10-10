package com.example.productrepair.dto;

import java.time.LocalDateTime;

public record RepairMessageResponse(Long id, String senderName, String senderRole,
                                    String content, LocalDateTime sentAt) {}
