package com.example.productrepair.repository;

import com.example.productrepair.entity.RepairStatusUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairStatusUpdateRepository extends JpaRepository<RepairStatusUpdate, Long> {
    List<RepairStatusUpdate> findByRepairIdOrderByUpdatedAtAsc(Long repairId);
}
