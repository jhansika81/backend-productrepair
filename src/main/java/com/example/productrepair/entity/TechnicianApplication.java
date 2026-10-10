package com.example.productrepair.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "technician_applications")
public class TechnicianApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 1500)
    private String serviceLocation;

    @Column(nullable = false, length = 2000)
    private String skills;

    @Column(nullable = false)
    private int yearsExperience;

    @Column(length = 2000)
    private String qualifications;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TechnicianApplicationStatus status = TechnicianApplicationStatus.PENDING;

    @Column(length = 1000)
    private String rejectionReason;

    private String documentName;
    private String documentStorageKey;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getServiceLocation() { return serviceLocation; }
    public void setServiceLocation(String serviceLocation) { this.serviceLocation = serviceLocation; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public int getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(int yearsExperience) { this.yearsExperience = yearsExperience; }
    public String getQualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }
    public TechnicianApplicationStatus getStatus() { return status; }
    public void setStatus(TechnicianApplicationStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getDocumentStorageKey() { return documentStorageKey; }
    public void setDocumentStorageKey(String documentStorageKey) { this.documentStorageKey = documentStorageKey; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
