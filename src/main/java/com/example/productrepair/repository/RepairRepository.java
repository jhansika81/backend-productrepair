
package com.example.productrepair.repository;

import com.example.productrepair.entity.Repair;
import com.example.productrepair.entity.RepairStatus;
import com.example.productrepair.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairRepository extends JpaRepository<Repair, Long> {

    List<Repair> findByCustomer(User customer);

    List<Repair> findByTechnician(User technician);

    List<Repair> findByStatus(RepairStatus status);
}
