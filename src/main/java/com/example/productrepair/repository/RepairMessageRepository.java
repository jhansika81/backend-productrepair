package com.example.productrepair.repository;

import com.example.productrepair.entity.RepairMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairMessageRepository extends JpaRepository<RepairMessage, Long> {
    List<RepairMessage> findByRepairIdOrderBySentAtAsc(Long repairId);
}
