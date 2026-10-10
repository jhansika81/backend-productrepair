package com.example.productrepair.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "technician_application_events")
public class TechnicianApplicationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private TechnicianApplication application;

    @ManyToOne(optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TechnicianApplicationStatus status;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public TechnicianApplicationEvent() {}
    public TechnicianApplicationEvent(TechnicianApplication application, User changedBy,
                                      TechnicianApplicationStatus status, String reason) {
        this.application = application;
        this.changedBy = changedBy;
        this.status = status;
        this.reason = reason;
    }

    public TechnicianApplication getApplication() { return application; }
    public User getChangedBy() { return changedBy; }
    public TechnicianApplicationStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
