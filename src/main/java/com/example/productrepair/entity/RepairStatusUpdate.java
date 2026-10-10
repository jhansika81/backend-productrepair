package com.example.productrepair.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "repair_status_updates")
public class RepairStatusUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "repair_id", nullable = false)
    private Repair repair;

    @ManyToOne(optional = false)
    @JoinColumn(name = "updated_by_id", nullable = false)
    private User updatedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairStatus status;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public RepairStatusUpdate() {}
    public RepairStatusUpdate(Repair repair, User updatedBy, RepairStatus status, String note) {
        this.repair = repair;
        this.updatedBy = updatedBy;
        this.status = status;
        this.note = note;
    }

    public Repair getRepair() { return repair; }
    public User getUpdatedBy() { return updatedBy; }
    public RepairStatus getStatus() { return status; }
    public String getNote() { return note; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
