package com.example.productrepair.repository;

import com.example.productrepair.entity.TechnicianApplicationEvent;
import com.example.productrepair.entity.TechnicianApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TechnicianApplicationEventRepository extends JpaRepository<TechnicianApplicationEvent, Long> {
    Optional<TechnicianApplicationEvent> findFirstByApplication_IdAndStatusOrderByChangedAtDesc(
            Long applicationId, TechnicianApplicationStatus status);
}
