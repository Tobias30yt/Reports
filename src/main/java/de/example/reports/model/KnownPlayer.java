package de.example.reports.model;
import java.time.Instant; import java.util.UUID;
public record KnownPlayer(UUID uuid, String name, Instant firstSeen, Instant lastSeen) {}
