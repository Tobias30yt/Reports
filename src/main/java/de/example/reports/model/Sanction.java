package de.example.reports.model;

import java.time.Instant;
import java.util.UUID;

public record Sanction(UUID playerUuid, String playerName, Type type, Instant expiresAt, String reason) {
 public enum Type { BAN, MUTE }
 public boolean permanent(){return expiresAt==null;}
 public boolean active(){return permanent()||expiresAt.isAfter(Instant.now());}
}
