package com.example.weatherdashboard.controller;

import com.example.weatherdashboard.model.User;
import com.example.weatherdashboard.model.UserPreferences;
import com.example.weatherdashboard.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/dashboard")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private com.example.weatherdashboard.service.OAuthUserService oAuthUserService;

    @GetMapping("/profile")
    public String profile(Model model, Authentication authentication, HttpServletRequest request) {
        try {
            if (authentication == null) {
                logger.error("Authentication is null in profile page access");
                model.addAttribute("error", "Session expired or not authenticated. Please log in again.");
                return "redirect:/login";
            }
            String principal = authentication.getName();
            logger.info("=== PROFILE ACCESS ===");
            logger.info("Auth principal: {}", principal);

            User user = null;
            String githubLogin = null;

            // Robust OAuth2 handling for all providers
            if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauthToken) {
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User = oauthToken.getPrincipal();
                String email = oauth2User.getAttribute("email");
                String login = oauth2User.getAttribute("login");
                githubLogin = login;

                // 1. Try by email
                if (email != null) {
                    user = userService.userRepository.findByEmail(email).orElse(null);
                }
                // 2. Try by login as username
                if (user == null && login != null) {
                    user = userService.userRepository.findByUsername(login).orElse(null);
                }
                // 3. Try by login@github.com as email
                if (user == null && login != null) {
                    String pseudoEmail = login + "@github.com";
                    user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                    if (user != null) {
                        logger.info("Found GitHub OAuth user by pseudo-email: {}", pseudoEmail);
                    }
                }
            }

            // 4. Try by principal as username
            if (user == null && principal != null && !principal.contains("@")) {
                user = userService.userRepository.findByUsername(principal).orElse(null);
            }
            // 5. Try by principal as email
            if (user == null && principal != null && principal.contains("@")) {
                user = userService.userRepository.findByEmail(principal).orElse(null);
            }
            // 6. FINAL fallback: If OAuth2 login is present, try login@github.com as email
            if (user == null && githubLogin != null) {
                String pseudoEmail = githubLogin + "@github.com";
                user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                if (user != null) {
                    logger.info("Final fallback: Found GitHub OAuth user by pseudo-email: {}", pseudoEmail);
                }
            }

            // Fallback: If user still not found, create missing OAuth user if principal looks like email
            if (user == null && principal != null && principal.contains("@")) {
                user = createMissingOAuthUser(principal, request);
                if (user != null) {
                    logger.info("Created missing OAuth user for principal: {}", principal);
                }
            }

            if (user == null) {
                logger.error("User not found for principal: {}", principal);
                model.addAttribute("error", "User profile not found. Please try logging in again.");
                return "redirect:/login";
            }

            if (user.getPreferences() == null) {
                user.setPreferences(new com.example.weatherdashboard.model.UserPreferences());
                try {
                    userService.userRepository.save(user);
                } catch (Exception e) {
                    logger.error("Error saving user preferences", e);
                }
            }

            model.addAttribute("user", user);
            model.addAttribute("oauthProvider", user.getOauth() != null ? user.getOauth() : "local");

            // IMPORTANT: Add CSRF token to model for proper handling
            org.springframework.security.web.csrf.CsrfToken csrfToken =
                    (org.springframework.security.web.csrf.CsrfToken) request.getAttribute("_csrf");
            if (csrfToken != null) {
                model.addAttribute("_csrf", csrfToken);
                logger.info("CSRF token added to model: {}", csrfToken.getToken());
            } else {
                logger.warn("No CSRF token found in request");
            }

        } catch (Exception e) {
            logger.error("Error loading profile for principal: {}", authentication.getName(), e);
            model.addAttribute("error", "Error loading profile: " + e.getMessage());
        }

        return "dashboard/profile";
    }

    private User createMissingOAuthUser(String email, jakarta.servlet.http.HttpServletRequest request) {
        try {
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

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(""); // OAuth users don't have passwords

            // Detect provider: github or google
            String provider = "google";
            if (email.endsWith("@github.com") || (request != null && request.getHeader("User-Agent") != null && request.getHeader("User-Agent").toLowerCase().contains("github"))) {
                provider = "github";
            }
            user.setOauth(provider);

            user.setCreatedAt(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());
            user.setPreferences(new UserPreferences());

            // Set default role
            user.getRoles().add("USER");

            // Save and return
            return userService.userRepository.save(user);

        } catch (Exception e) {
            logger.error("Error creating missing OAuth user", e);
            return null;
        }
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute("user") User user,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Authentication authentication) {

        logger.info("=== UPDATING PROFILE ===");
        logger.info("Auth principal: {}", authentication.getName());

        try {
            // Basic email validation
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Email cannot be empty");
                return "redirect:/dashboard/profile";
            }

            if (!user.getEmail().contains("@")) {
                redirectAttributes.addFlashAttribute("error", "Please enter a valid email address");
                return "redirect:/dashboard/profile";
            }

            // Get the actual username to update
            String principal = authentication.getName();
            String actualUsername = null;

            // Find the actual username
            if (principal.contains("@")) {
                User existingUser = userService.userRepository.findByEmail(principal).orElse(null);
                if (existingUser != null) {
                    actualUsername = existingUser.getUsername();
                }
            } else {
                actualUsername = principal;
            }

            if (actualUsername == null) {
                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/dashboard/profile";
            }

            // Use specific email update method
            User savedUser = userService.updateUserEmail(actualUsername, user.getEmail().trim());
            logger.info("Profile updated successfully for user: {}", savedUser.getUsername());

            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        } catch (Exception e) {
            logger.error("Error updating profile for user: {}", authentication.getName(), e);
            redirectAttributes.addFlashAttribute("error", "Error updating profile: " + e.getMessage());
        }

        return "redirect:/dashboard/profile";
    }

    @PostMapping("/profile/preferences")
    public String updatePreferences(@ModelAttribute("preferences") UserPreferences preferences,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Authentication authentication) {

        logger.info("=== UPDATING PREFERENCES ===");
        logger.info("Auth principal: {}", authentication.getName());
        logger.info("Received preferences: {}", preferences);

        try {
            // Validate preferences
            if (preferences.getTemperatureUnit() == null || preferences.getTemperatureUnit().trim().isEmpty()) {
                preferences.setTemperatureUnit("celsius");
            }
            if (preferences.getWindSpeedUnit() == null || preferences.getWindSpeedUnit().trim().isEmpty()) {
                preferences.setWindSpeedUnit("kmh");
            }
            if (preferences.getTimeFormat() == null || preferences.getTimeFormat().trim().isEmpty()) {
                preferences.setTimeFormat("24h");
            }
            if (preferences.getTheme() == null || preferences.getTheme().trim().isEmpty()) {
                preferences.setTheme("light");
            }

            // Get the actual username to update
            String principal = authentication.getName();
            String actualUsername = null;

            // Find the actual username
            if (principal.contains("@")) {
                User existingUser = userService.userRepository.findByEmail(principal).orElse(null);
                if (existingUser != null) {
                    actualUsername = existingUser.getUsername();
                }
            } else {
                actualUsername = principal;
            }

            if (actualUsername == null) {
                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/dashboard/profile";
            }

            // Use specific preferences update method
            User savedUser = userService.updateUserPreferences(actualUsername, preferences);

            if (savedUser != null && savedUser.getPreferences() != null) {
                logger.info("Preferences saved successfully: {}", savedUser.getPreferences());
                redirectAttributes.addFlashAttribute("success",
                        "Preferences updated successfully! Temperature will be displayed in " +
                                preferences.getTemperatureUnit() + " on your next weather search.");
            } else {
                logger.error("Failed to save preferences - savedUser or preferences is null");
                redirectAttributes.addFlashAttribute("error", "Failed to save preferences");
            }

        } catch (Exception e) {
            logger.error("Error updating preferences for user: {}", authentication.getName(), e);
            redirectAttributes.addFlashAttribute("error", "Error updating preferences: " + e.getMessage());
        }
        return "redirect:/dashboard/profile";
    }

    @PostMapping("/change-password")
    public String changePasswordForm(@RequestParam String currentPassword,
                                     @RequestParam String newPassword,
                                     RedirectAttributes redirectAttributes,
                                     Authentication authentication) {
        try {
            String username = authentication.getName();
            userService.updatePassword(username, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
        } catch (Exception e) {
            logger.error("Error changing password via form", e);
            redirectAttributes.addFlashAttribute("error", "Error changing password: " + e.getMessage());
        }
        return "redirect:/dashboard/profile";
    }

    @PostMapping("/delete-account")
    public String deleteAccount(Authentication authentication, HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        try {
            String principal = authentication.getName();
            User user = null;
            if (principal.contains("@")) {
                user = userService.userRepository.findByEmail(principal).orElse(null);
            } else {
                user = userService.userRepository.findByUsername(principal).orElse(null);
            }
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/dashboard/profile";
            }
            userService.userRepository.delete(user);
            // Log out the user after deletion
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, auth);
            }
            redirectAttributes.addFlashAttribute("message", "Your account has been deleted successfully.");
            return "redirect:/login?accountDeleted";
        } catch (Exception e) {
            logger.error("Error deleting account", e);
            redirectAttributes.addFlashAttribute("error", "Error deleting account: " + e.getMessage());
            return "redirect:/dashboard/profile";
        }
    }
}

@RestController
@RequestMapping("/api/user")
class UserApiController {
    private final UserService userService;

    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/upgrade")
    public Map<String, Object> upgradeToPremium(Authentication authentication) {
        try {
            String principal = authentication.getName();
            logger.info("=== PREMIUM UPGRADE REQUEST ===");
            logger.info("Principal: {}", principal);

            User user = null;
            String githubLogin = null;
            String oauthEmail = null;
            boolean isOAuth = false;

            if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauthToken) {
                isOAuth = true;
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User = oauthToken.getPrincipal();
                oauthEmail = oauth2User.getAttribute("email");
                String login = oauth2User.getAttribute("login");
                githubLogin = login;

                // For Google: always use email attribute for lookup
                if (oauthEmail != null) {
                    user = userService.userRepository.findByEmail(oauthEmail).orElse(null);
                }
                // For GitHub: try login as username
                if (user == null && login != null) {
                    user = userService.userRepository.findByUsername(login).orElse(null);
                }
                // For GitHub: try login@github.com as email
                if (user == null && login != null) {
                    String pseudoEmail = login + "@github.com";
                    user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                    if (user != null) {
                        logger.info("Found GitHub OAuth user by pseudo-email for upgrade: {}", pseudoEmail);
                    }
                }
            }

            // Fallback for local users or if OAuth lookups failed
            if (user == null && principal != null && !principal.contains("@")) {
                // If principal is a numeric string (Google ID), skip this for OAuth
                if (!isOAuth || !principal.matches("\\d+")) {
                    user = userService.userRepository.findByUsername(principal).orElse(null);
                }
            }
            if (user == null && principal != null && principal.contains("@")) {
                user = userService.userRepository.findByEmail(principal).orElse(null);
            }
            if (user == null && githubLogin != null) {
                String pseudoEmail = githubLogin + "@github.com";
                user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                if (user != null) {
                    logger.info("Final fallback: Found GitHub OAuth user by pseudo-email for upgrade: {}", pseudoEmail);
                }
            }

            if (user == null) {
                logger.error("User not found for upgrade: {}", principal);
                return Map.of("status", "error", "message", "User not found");
            }

            logger.info("Upgrading user: {} (current roles: {})", user.getUsername(), user.getRoles());

            user.setPremium(true);
            User updatedUser = userService.updateUser(user);

            logger.info("User upgraded successfully: {} (new roles: {})", updatedUser.getUsername(), updatedUser.getRoles());

            // Update the Authentication in the SecurityContext with new roles
            java.util.Set<String> roles = updatedUser.getRoles();
            java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
            for (String role : roles) {
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
            }
            org.springframework.security.core.Authentication newAuth;
            if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauthToken) {
                // For OAuth users, preserve the principal and details
                newAuth = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                        oauthToken.getPrincipal(), authorities, oauthToken.getAuthorizedClientRegistrationId()
                );
            } else {
                // For local users
                newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        updatedUser.getUsername(), null, authorities
                );
            }
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(newAuth);

            return Map.of("status", "upgraded", "roles", updatedUser.getRoles());
        } catch (Exception e) {
            logger.error("Error upgrading user to premium", e);
            return Map.of("status", "error", "message", "Upgrade failed: " + e.getMessage());
        }
    }

    @GetMapping("/test")
    public Map<String, Object> testEndpoint(
            HttpServletRequest request,
            Authentication authentication) {

        logger.info("=== TEST ENDPOINT ACCESS ===");
        logger.info("Authentication: {}", authentication);
        logger.info("Request headers: {}", java.util.Collections.list(request.getHeaderNames()));

        return Map.of(
                "status", "success",
                "message", "Authentication working",
                "user", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }

    // Test POST endpoint without @AuthenticationPrincipal
    @PostMapping("/test-post")
    public Map<String, Object> testPost(
            HttpServletRequest request,
            Authentication authentication,
            @RequestBody Map<String, String> data) {

        logger.info("=== TEST POST ENDPOINT ===");
        logger.info("Authentication: {}", authentication);
        logger.info("Data received: {}", data);

        return Map.of(
                "status", "success",
                "message", "POST working",
                "user", authentication.getName(),
                "receivedData", data
        );
    }

    // Simplified change password endpoint
    @PostMapping("/change-password-simple")
    public Map<String, Object> changePasswordSimple(
            HttpServletRequest request,
            Authentication authentication,
            @RequestBody Map<String, String> req) {

        try {
            logger.info("=== SIMPLE CHANGE PASSWORD ===");
            logger.info("Authentication: {}", authentication);
            logger.info("User: {}", authentication.getName());

            String username = authentication.getName();
            String currentPassword = req.get("currentPassword");
            String newPassword = req.get("newPassword");

            logger.info("Attempting password change for user: {}", username);

            // Validate inputs
            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                return Map.of("status", "error", "message", "Current password is required");
            }

            if (newPassword == null || newPassword.trim().isEmpty()) {
                return Map.of("status", "error", "message", "New password is required");
            }

            if (newPassword.length() < 6) {
                return Map.of("status", "error", "message", "New password must be at least 6 characters");
            }

            // Try to change password
            userService.updatePassword(username, currentPassword, newPassword);

            logger.info("Password changed successfully for user: {}", username);
            return Map.of("status", "success", "message", "Password updated successfully");

        } catch (Exception e) {
            logger.error("Error changing password", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(
            HttpServletRequest request,
            Authentication authentication,
            @RequestBody Map<String, String> req) {

        try {
            logger.info("=== CHANGE PASSWORD ATTEMPT ===");
            logger.info("Request URI: {}", request.getRequestURI());
            logger.info("Request Method: {}", request.getMethod());
            logger.info("Authentication: {}", authentication);
            logger.info("Auth Name: {}", authentication != null ? authentication.getName() : "null");

            if (authentication == null) {
                logger.error("No authentication found");
                return Map.of("status", "error", "message", "Authentication required");
            }

            String username = authentication.getName();
            logger.info("Username: {}", username);

            String currentPassword = req.get("currentPassword");
            String newPassword = req.get("newPassword");

            if (currentPassword == null || newPassword == null) {
                return Map.of("status", "error", "message", "Both current and new passwords are required");
            }

            userService.updatePassword(username, currentPassword, newPassword);
            logger.info("Password changed successfully for user: {}", username);
            return Map.of("status", "success", "message", "Password updated successfully");

        } catch (Exception e) {
            logger.error("Error changing password", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @GetMapping("/auth-status")
    public Map<String, Object> authStatus(
            HttpServletRequest request,
            Authentication authentication) {

        Map<String, Object> status = new HashMap<>();
        status.put("authentication", authentication != null ? authentication.getName() : "null");
        status.put("authorities", authentication != null ? authentication.getAuthorities() : "null");
        status.put("sessionId", request.getSession(false) != null ? request.getSession(false).getId() : "null");

        // Check CSRF
        String csrfCookie = null;
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("XSRF-TOKEN".equals(c.getName())) {
                    csrfCookie = c.getValue();
                    break;
                }
            }
        }
        status.put("csrfCookie", csrfCookie);

        return status;
    }


    @GetMapping("/csrf-debug")
    public Map<String, Object> csrfDebug(jakarta.servlet.http.HttpServletRequest request) {
        String csrfCookie = null;
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("XSRF-TOKEN".equals(c.getName())) csrfCookie = c.getValue();
            }
        }
        String sessionId = request.getSession(false) != null ? request.getSession(false).getId() : "none";
        return Map.of(
            "csrfCookie", csrfCookie,
            "sessionId", sessionId,
            "user", request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "none"
        );
    }
}

@RestController
@RequestMapping("/api/admin")
class AdminApiController {
    private final UserService userService;

    private static final Logger logger = LoggerFactory.getLogger(AdminApiController.class);

    public AdminApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public java.util.List<User> listAllUsers() {
        try {
            return userService.userRepository.findAll();
        } catch (Exception e) {
            logger.error("Error listing users", e);
            return java.util.List.of();
        }
    }

    @PostMapping("/users/{username}/roles")
    public Map<String, Object> updateUserRoles(@PathVariable String username, @RequestBody Map<String, String> req) {
        try {
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            String rolesStr = req.getOrDefault("roles", "USER");
            user.setRolesFromString(rolesStr);
            User updatedUser = userService.updateUser(user);

            return Map.of("status", "updated", "roles", updatedUser.getRoles());
        } catch (Exception e) {
            logger.error("Error updating user roles", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @DeleteMapping("/users/{username}")
    public Map<String, Object> deleteUser(@PathVariable String username) {
        try {
            User user = userService.userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            userService.userRepository.delete(user);
            return Map.of("status", "deleted");
        } catch (Exception e) {
            logger.error("Error deleting user", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}