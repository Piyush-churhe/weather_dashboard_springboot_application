package com.example.weatherdashboard.controller;

import com.example.weatherdashboard.model.User;
import com.example.weatherdashboard.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Autowired
    private UserService userService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/users")
    public Map<String, Object> getAllUsers() {
        Map<String, Object> result = new HashMap<>();

        try {
            List<User> users = userService.userRepository.findAll();
            result.put("totalUsers", users.size());
            result.put("users", users);
            result.put("status", "success");
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return result;
    }

    @GetMapping("/current-user")
    public Map<String, Object> getCurrentUser() {
        Map<String, Object> result = new HashMap<>();

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            result.put("authUsername", username);
            result.put("authName", auth.getName());
            result.put("authorities", auth.getAuthorities());

            // Try to find by username
            User userByUsername = userService.findByUsername(username).orElse(null);
            result.put("userByUsername", userByUsername);

            // If username looks like email, try to find by email
            if (username.contains("@")) {
                User userByEmail = userService.userRepository.findByEmail(username).orElse(null);
                result.put("userByEmail", userByEmail);
            }

            result.put("status", "success");
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return result;
    }

    @GetMapping("/db-connection")
    public Map<String, Object> testDatabaseConnection() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Test connection by counting documents
            long userCount = mongoTemplate.count(new Query(), User.class);
            result.put("userCount", userCount);
            result.put("status", "connected");

            // Test database name
            String dbName = mongoTemplate.getDb().getName();
            result.put("databaseName", dbName);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "connection_failed");
        }

        return result;
    }

    @PostMapping("/create-test-user")
    public Map<String, Object> createTestUser() {
        Map<String, Object> result = new HashMap<>();

        try {
            String testEmail = "test-oauth-" + System.currentTimeMillis() + "@example.com";
            String testUsername = "testuser" + System.currentTimeMillis();

            User testUser = new User();
            testUser.setUsername(testUsername);
            testUser.setEmail(testEmail);
            testUser.setPassword("");
            testUser.setOauth("test");
            testUser.getRoles().add("USER");

            User savedUser = userService.userRepository.save(testUser);

            result.put("createdUser", savedUser);
            result.put("status", "success");

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
            e.printStackTrace();
        }

        return result;
    }

    @GetMapping("/check-roles")
    public Map<String, Object> checkCurrentUserRoles() {
        Map<String, Object> result = new HashMap<>();

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            result.put("authUsername", username);
            result.put("authAuthorities", auth.getAuthorities());

            // Try to find user by username first
            User user = userService.findByUsername(username).orElse(null);

            // If not found and looks like email, try by email
            if (user == null && username.contains("@")) {
                user = userService.userRepository.findByEmail(username).orElse(null);
            }

            if (user != null) {
                result.put("userRoles", user.getRoles());
                result.put("isPremium", user.isPremium());
                result.put("isAdmin", user.isAdmin());
                result.put("hasRole_USER", user.hasRole("USER"));
                result.put("hasRole_PREMIUM", user.hasRole("PREMIUM"));
                result.put("hasRole_ADMIN", user.hasRole("ADMIN"));
                result.put("oauth", user.getOauth());
                result.put("username", user.getUsername());
                result.put("email", user.getEmail());
                result.put("userFound", true);
            } else {
                result.put("userFound", false);
                result.put("error", "User not found in database");
            }

            result.put("status", "success");
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return result;
    }

    @PostMapping("/force-upgrade/{username}")
    public Map<String, Object> forceUpgradeUser(@PathVariable String username) {
        Map<String, Object> result = new HashMap<>();

        try {
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            user.getRoles().add("PREMIUM");
            User savedUser = userService.updateUser(user);

            result.put("status", "success");
            result.put("message", "User " + username + " upgraded to Premium");
            result.put("newRoles", savedUser.getRoles());

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return result;
    }

    @PostMapping("/create-oauth-user")
    public Map<String, Object> createOAuthUser(@RequestParam String email) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Check if user already exists
            if (userService.userRepository.findByEmail(email).isPresent()) {
                result.put("error", "User already exists with email: " + email);
                result.put("status", "exists");
                return result;
            }

            // Generate username from email
            String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
            if (baseUsername.isEmpty()) {
                baseUsername = "user";
            }

            // Ensure username is unique
            String username = baseUsername;
            int counter = 1;
            while (userService.userRepository.existsByUsername(username)) {
                username = baseUsername + counter;
                counter++;
            }

            User oauthUser = new User();
            oauthUser.setUsername(username);
            oauthUser.setEmail(email);
            oauthUser.setPassword(""); // OAuth user
            oauthUser.setOauth("google");
            oauthUser.setCreatedAt(java.time.LocalDateTime.now());
            oauthUser.setLastLogin(java.time.LocalDateTime.now());
            oauthUser.getRoles().add("USER");
            oauthUser.setPreferences(new com.example.weatherdashboard.model.UserPreferences());

            User savedUser = userService.userRepository.save(oauthUser);

            result.put("createdUser", savedUser);
            result.put("status", "success");
            result.put("message", "OAuth user created successfully");

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
            e.printStackTrace();
        }

        return result;
    }

    @GetMapping("/test-oauth/{email}")
    public Map<String, Object> testOAuthUserLookup(@PathVariable String email) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<User> user = userService.userRepository.findByEmail(email);

            if (user.isPresent()) {
                result.put("userFound", true);
                result.put("user", user.get());
                result.put("status", "success");
            } else {
                result.put("userFound", false);
                result.put("message", "User not found with email: " + email);
                result.put("status", "not_found");
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return result;
    }
}