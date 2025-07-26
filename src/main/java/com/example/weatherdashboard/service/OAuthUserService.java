package com.example.weatherdashboard.service;

import com.example.weatherdashboard.model.User;
import com.example.weatherdashboard.model.UserPreferences;
import com.example.weatherdashboard.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;

@Service
public class OAuthUserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthUserService.class);

    @Autowired
    private UserRepository userRepository;

    public UserRepository getUserRepository() {
        return userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            // Process and save the OAuth2 user
            User savedUser = processOAuth2User(userRequest, oAuth2User);
            logger.info("OAuth user processing completed for: {}", savedUser != null ? savedUser.getEmail() : "unknown");
        } catch (Exception e) {
            logger.error("Error processing OAuth2 user", e);
            // Don't prevent login if user processing fails
        }

        return oAuth2User;
    }

    private User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // Special handling for GitHub
        if ("github".equals(registrationId)) {
            if (email == null || email.trim().isEmpty()) {
                // Fallback: use GitHub login as pseudo-email
                String login = oAuth2User.getAttribute("login");
                if (login != null) {
                    email = login + "@github.com";
                }
            }
            if (name == null || name.trim().isEmpty()) {
                name = oAuth2User.getAttribute("login");
            }
        }

        logger.info("=== PROCESSING OAUTH USER ===");
        logger.info("Email: {}, Name: {}, Provider: {}", email, name, registrationId);

        if (email == null || email.trim().isEmpty()) {
            logger.error("OAuth2 user has no email attribute");
            throw new RuntimeException("Email is required for OAuth authentication");
        }

        try {
            // Check if user exists by email
            Optional<User> existingUser = userRepository.findByEmail(email);

            if (existingUser.isPresent()) {
                // Update existing user
                User user = existingUser.get();
                user.setLastLogin(LocalDateTime.now());
                user.setOauth(registrationId);

                User savedUser = userRepository.save(user);
                logger.info("Updated existing OAuth user: {} (ID: {})", savedUser.getUsername(), savedUser.getId());
                return savedUser;
            } else {
                // Create new user
                User newUser = createNewOAuthUser(email, name, registrationId);

                logger.info("Attempting to save new OAuth user to database...");
                User savedUser = userRepository.save(newUser);
                logger.info("Successfully created new OAuth user: {} with ID: {}", savedUser.getUsername(), savedUser.getId());

                // Verify the user was actually saved
                Optional<User> verifyUser = userRepository.findByEmail(email);
                if (verifyUser.isPresent()) {
                    logger.info("Verification successful - user found in database with ID: {}", verifyUser.get().getId());
                    return verifyUser.get();
                } else {
                    logger.error("Verification failed - user not found in database after save");
                    throw new RuntimeException("Failed to save OAuth user to database");
                }
            }

        } catch (Exception e) {
            logger.error("Error saving OAuth user to database: {}", e.getMessage(), e);
            throw new RuntimeException("Database error during OAuth user creation", e);
        }
    }

    private User createNewOAuthUser(String email, String name, String provider) {
        // Generate username from email
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        if (baseUsername.isEmpty()) {
            baseUsername = "user";
        }

        // Ensure username is unique
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }

        logger.info("Creating new OAuth user with username: {}, email: {}", username, email);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(""); // OAuth users don't have passwords
        user.setOauth(provider);
        user.setCreatedAt(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());

        // Initialize roles - OAuth users start as regular users
        user.setRoles(new HashSet<>());
        user.getRoles().add("USER");

        // Only specific admin emails get admin/premium roles
        if ("admin@example.com".equalsIgnoreCase(email)) {
            user.getRoles().add("ADMIN");
            user.getRoles().add("PREMIUM");
            logger.info("Granted ADMIN and PREMIUM roles to {}", email);
        } else {
            logger.info("OAuth user {} created with USER role only", email);
        }

        // Initialize preferences
        UserPreferences preferences = new UserPreferences();
        user.setPreferences(preferences);

        return user;
    }

    public User findOrCreateOAuthUser(String email, String provider) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        } else {
            User newUser = createNewOAuthUser(email, null, provider);
            return userRepository.save(newUser);
        }
    }
}