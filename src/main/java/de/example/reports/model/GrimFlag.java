package de.example.reports.model;

import java.time.Instant;
import java.util.UUID;

public record GrimFlag(long id, UUID playerUuid, String playerName, String checkName, String details, Instant timestamp) {
}
