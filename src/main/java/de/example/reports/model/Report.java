package de.example.reports.model;
import java.time.Instant; import java.util.UUID;
public record Report(long id, UUID reportedUuid, String reportedName, UUID reporterUuid, String reporterName, String reason, Instant timestamp, ReportStatus status, String grimFlags) {}
