package ch.amato.ccp.model;

import java.io.Serializable;
import java.time.Instant;

public record Dispute(String transactionId, DisputeReason reason, String comment,
                      Instant createdAt) implements Serializable {
}
