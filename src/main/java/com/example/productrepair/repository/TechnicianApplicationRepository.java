package com.example.productrepair.repository;

import com.example.productrepair.entity.TechnicianApplication;
import com.example.productrepair.entity.TechnicianApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TechnicianApplicationRepository extends JpaRepository<TechnicianApplication, Long> {
    Optional<TechnicianApplication> findByUserId(Long userId);
    List<TechnicianApplication> findByStatusOrderBySubmittedAtAsc(TechnicianApplicationStatus status);
}

