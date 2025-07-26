package com.example.weatherdashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Indexed(unique = true)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Indexed(unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @Valid
    private UserPreferences preferences;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    private java.util.Set<String> roles = new java.util.HashSet<>(); // Roles: USER, ADMIN, PREMIUM
    private String oauth = "local"; // 'local' or 'google' or other providers

    // Constructors
    public User() {
        this.preferences = new UserPreferences();
        this.createdAt = LocalDateTime.now();
        this.roles = new java.util.HashSet<>();
        this.roles.add("USER");
    }

    public User(String username, String email, String password) {
        this();
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public UserPreferences getPreferences() {
        if (preferences == null) {
            preferences = new UserPreferences();
        }
        return preferences;
    }

    public void setPreferences(UserPreferences preferences) {
        this.preferences = preferences;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public java.util.Set<String> getRoles() {
        return roles;
    }

    public void setRoles(java.util.Set<String> roles) {
        this.roles = roles;
    }

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }

    public boolean isPremium() {
        return roles != null && roles.contains("PREMIUM");
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public void addRole(String role) {
        if (this.roles == null) this.roles = new java.util.HashSet<>();
        this.roles.add(role);
    }

    public void removeRole(String role) {
        if (this.roles != null) this.roles.remove(role);
    }

    public String getRoleList() {
        return roles == null ? "" : String.join(", ", roles);
    }

    public void setAdmin(boolean isAdmin) {
        if (isAdmin) {
            addRole("ADMIN");
        } else {
            removeRole("ADMIN");
        }
    }

    public void setPremium(boolean isPremium) {
        if (isPremium) {
            addRole("PREMIUM");
        } else {
            removeRole("PREMIUM");
        }
    }

    public void setRolesFromString(String rolesStr) {
        if (rolesStr == null || rolesStr.isBlank()) {
            this.roles = new java.util.HashSet<>();
        } else {
            this.roles = new java.util.HashSet<>();
            for (String role : rolesStr.split(",")) {
                this.roles.add(role.trim().toUpperCase());
            }
        }
    }

    public String getOauth() {
        return oauth;
    }
    public void setOauth(String oauth) {
        this.oauth = oauth;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", preferences=" + preferences +
                ", createdAt=" + createdAt +
                ", lastLogin=" + lastLogin +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        User user = (User) obj;
        return username != null ? username.equals(user.username) : user.username == null;
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}