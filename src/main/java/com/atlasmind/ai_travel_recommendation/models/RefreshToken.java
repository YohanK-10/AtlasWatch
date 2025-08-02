package com.atlasmind.ai_travel_recommendation.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /**
     * The token string that will be sent to the client.
     */
    @Column(nullable = false, unique = true)
    private String token; // Store as a hash

    private LocalDateTime expiryTime;

    @ManyToOne(fetch = FetchType.LAZY) // A user can have multiple refresh tokens.
    @JoinColumn(name = "user_id", nullable = false) // references the primary key of the Users table
    private User user;

    /**
     * Marks whether the refresh token has been revoked or used.  When using
     * refresh token rotation, the old token is revoked when it is exchanged
     * for a new pair of tokens.
     */
    private boolean revoked;
}
