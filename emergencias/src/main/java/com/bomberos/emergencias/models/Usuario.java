package com.bomberos.emergencias.models;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Data
@Entity
@Table(name = "usuarios")
public class Usuario implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordHash;

    @Column(nullable = false)
    private String status = "INACTIVE";

    private boolean mfaEnabled = false;

    private String mfaSecret;

    private String pendingMfaSecret;

    private String verificationCode;

    private LocalDateTime expiresAt;

    private int verificationAttempts = 0;

    private LocalDateTime activatedAt;

    private String resetToken;

    private LocalDateTime resetTokenExpiresAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}
