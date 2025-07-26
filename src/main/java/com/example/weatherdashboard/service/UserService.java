package com.example.weatherdashboard.service;

import com.example.weatherdashboard.model.User;
import com.example.weatherdashboard.model.UserPreferences;
import com.example.weatherdashboard.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import jakarta.annotation.PostConstruct;

@Service
public class UserService implements UserDetailsService {

    public static final Logger logger = LoggerFactory.getLogger(UserService.class);

    public final UserRepository userRepository;
    public final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = null;

        // First try to find by username
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else if (username.contains("@")) {
            // If username looks like an email (OAuth case), try to find by email
            userOpt = userRepository.findByEmail(username);
            if (userOpt.isPresent()) {
                user = userOpt.get();
            }
        }

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        java.util.Set<String> roles = user.getRoles() != null ? user.getRoles() : java.util.Set.of("USER");
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
        for (String role : roles) {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
        }

        // For OAuth users, password might be empty, so use a placeholder
        String password = user.getPassword();
        if (password == null || password.isEmpty()) {
            password = "oauth-user"; // Placeholder for OAuth users
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(password)
                .authorities(authorities)
                .build();
    }

    public User registerUser(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User(username, email, passwordEncoder.encode(password));
        user.getRoles().add("USER"); // Ensure USER role is set

        // Ensure preferences are initialized
        if (user.getPreferences() == null) {
            user.setPreferences(new UserPreferences());
        }

        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        try {
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            logger.error("Error finding user by username: {}", username, e);
            return Optional.empty();
        }
    }

    /**
     * Find user by email or username (for OAuth compatibility)
     */
    public Optional<User> findByUsernameOrEmail(String identifier) {
        try {
            // First try by username
            Optional<User> user = userRepository.findByUsername(identifier);
            if (user.isPresent()) {
                return user;
            }

            // If not found and looks like email, try by email
            if (identifier.contains("@")) {
                return userRepository.findByEmail(identifier);
            }

            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error finding user by username or email: {}", identifier, e);
            return Optional.empty();
        }
    }

    public User updateLastLogin(String username) {
        User user = findByUsernameOrEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Enhanced update method with multiple approaches
     */
    public User updateUser(User user) {
        try {
            logger.info("=== UPDATE USER ATTEMPT ===");
            logger.info("User ID: {}", user.getId());
            logger.info("Username: {}", user.getUsername());
            logger.info("Preferences: {}", user.getPreferences());

            // Ensure preferences are not null
            if (user.getPreferences() == null) {
                user.setPreferences(new UserPreferences());
            }

            // Method 1: Direct save (should work since signup works)
            User savedUser = userRepository.save(user);
            logger.info("Direct save result: {}", savedUser != null ? "SUCCESS" : "FAILED");

            if (savedUser != null) {
                // Verify the update actually worked
                Optional<User> verifyUser = userRepository.findByUsername(user.getUsername());
                if (verifyUser.isPresent()) {
                    logger.info("Verification - saved preferences: {}", verifyUser.get().getPreferences());
                    return verifyUser.get();
                } else {
                    logger.error("User not found during verification!");
                }
            }

            // Method 2: If direct save failed, try MongoDB template update
            logger.warn("Direct save failed, trying MongoDB template update...");
            return updateUserWithTemplate(user);

        } catch (Exception e) {
            logger.error("Error in updateUser, trying alternative method", e);
            return updateUserWithTemplate(user);
        }
    }

    /**
     * Alternative update method using MongoTemplate for direct field updates
     */
    private User updateUserWithTemplate(User user) {
        try {
            logger.info("Using MongoTemplate for update...");

            Query query = new Query(Criteria.where("username").is(user.getUsername()));
            Update update = new Update();

            // Update individual fields
            if (user.getEmail() != null) {
                update.set("email", user.getEmail());
                logger.info("Setting email: {}", user.getEmail());
            }

            if (user.getPreferences() != null) {
                update.set("preferences", user.getPreferences());
                logger.info("Setting preferences: {}", user.getPreferences());
            }

            if (user.getLastLogin() != null) {
                update.set("lastLogin", user.getLastLogin());
            }

            if (user.getRoles() != null) {
                update.set("roles", user.getRoles());
            }

            // Execute update
            var result = mongoTemplate.updateFirst(query, update, User.class);
            logger.info("MongoTemplate update result - matched: {}, modified: {}",
                    result.getMatchedCount(), result.getModifiedCount());

            if (result.getMatchedCount() > 0) {
                // Return updated user
                Optional<User> updatedUser = userRepository.findByUsername(user.getUsername());
                if (updatedUser.isPresent()) {
                    logger.info("MongoTemplate update SUCCESS");
                    return updatedUser.get();
                }
            }

            logger.error("MongoTemplate update failed");
            throw new RuntimeException("Failed to update user with MongoTemplate");

        } catch (Exception e) {
            logger.error("MongoTemplate update failed", e);
            throw new RuntimeException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Specific method for updating preferences only
     */
    public User updateUserPreferences(String username, UserPreferences preferences) {
        try {
            logger.info("=== UPDATING PREFERENCES ONLY ===");
            logger.info("Username: {}", username);
            logger.info("New preferences: {}", preferences);

            // Direct field update using MongoTemplate
            Query query = new Query(Criteria.where("username").is(username));
            Update update = new Update().set("preferences", preferences);

            var result = mongoTemplate.updateFirst(query, update, User.class);
            logger.info("Preferences update result - matched: {}, modified: {}",
                    result.getMatchedCount(), result.getModifiedCount());

            if (result.getModifiedCount() > 0) {
                Optional<User> updatedUser = userRepository.findByUsername(username);
                if (updatedUser.isPresent()) {
                    logger.info("Preferences update SUCCESS: {}", updatedUser.get().getPreferences());
                    return updatedUser.get();
                }
            }

            logger.error("Preferences update failed - no documents modified");
            throw new RuntimeException("Failed to update preferences");

        } catch (Exception e) {
            logger.error("Error updating preferences", e);
            throw new RuntimeException("Failed to update preferences: " + e.getMessage(), e);
        }
    }

    /**
     * Specific method for updating email only
     */
    public User updateUserEmail(String username, String newEmail) {
        try {
            logger.info("=== UPDATING EMAIL ONLY ===");
            logger.info("Username: {}, New email: {}", username, newEmail);

            Query query = new Query(Criteria.where("username").is(username));
            Update update = new Update().set("email", newEmail);

            var result = mongoTemplate.updateFirst(query, update, User.class);
            logger.info("Email update result - matched: {}, modified: {}",
                    result.getMatchedCount(), result.getModifiedCount());

            if (result.getModifiedCount() > 0) {
                Optional<User> updatedUser = userRepository.findByUsername(username);
                if (updatedUser.isPresent()) {
                    logger.info("Email update SUCCESS");
                    return updatedUser.get();
                }
            }

            throw new RuntimeException("Failed to update email");

        } catch (Exception e) {
            logger.error("Error updating email", e);
            throw new RuntimeException("Failed to update email: " + e.getMessage(), e);
        }
    }

    /**
     * Create or update OAuth user
     */
    public User createOrUpdateOAuthUser(String email, String name, String provider) {
        try {
            logger.info("Creating or updating OAuth user: email={}, name={}, provider={}", email, name, provider);

            // Check if user exists by email
            Optional<User> existingUser = userRepository.findByEmail(email);

            if (existingUser.isPresent()) {
                // Update existing user
                User user = existingUser.get();
                user.setLastLogin(LocalDateTime.now());
                user.setOauth(provider);
                return userRepository.save(user);
            } else {
                // Create new user
                String username = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");

                // Ensure username is unique
                String originalUsername = username;
                int counter = 1;
                while (userRepository.existsByUsername(username)) {
                    username = originalUsername + counter;
                    counter++;
                }

                User newUser = new User();
                newUser.setUsername(username);
                newUser.setEmail(email);
                newUser.setPassword(""); // No password for OAuth users
                newUser.setOauth(provider);
                newUser.setCreatedAt(LocalDateTime.now());
                newUser.setLastLogin(LocalDateTime.now());
                newUser.setPreferences(new UserPreferences());

                // Set default roles
                newUser.getRoles().add("USER");
                if ("admin@example.com".equalsIgnoreCase(email)) {
                    newUser.getRoles().add("ADMIN");
                    newUser.getRoles().add("PREMIUM");
                }

                return userRepository.save(newUser);
            }
        } catch (Exception e) {
            logger.error("Error creating/updating OAuth user", e);
            throw new RuntimeException("Failed to create/update OAuth user: " + e.getMessage());
        }
    }

    /**
     * Update password for local (non-OAuth) users
     */
    public void updatePassword(String username, String currentPassword, String newPassword) {
        User user = findByUsernameOrEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Only allow for local users
        if (user.getOauth() != null && !user.getOauth().equals("local")) {
            throw new RuntimeException("Password change is only allowed for local users");
        }

        // Validate current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Password strength check (at least 6 chars, can be improved)
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("Password updated successfully for user: {}", user.getUsername());
    }

    @PostConstruct
    public void createDefaultAdmin() {
        try {
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User("admin", "admin@example.com", passwordEncoder.encode("admin123"));
                admin.getRoles().add("ADMIN");
                admin.getRoles().add("PREMIUM");
                admin.setPreferences(new UserPreferences());
                userRepository.save(admin);
                logger.info("Created default admin user");
            }
        } catch (Exception e) {
            logger.error("Error creating default admin user", e);
        }
    }
}