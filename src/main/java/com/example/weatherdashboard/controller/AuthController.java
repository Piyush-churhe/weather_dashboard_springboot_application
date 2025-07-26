package com.example.weatherdashboard.controller;

import com.example.weatherdashboard.model.User;
import com.example.weatherdashboard.service.UserService;
import com.example.weatherdashboard.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EmailService emailService;

    // In-memory token store for password reset tokens
    private static final ConcurrentHashMap<String, String> resetTokens = new ConcurrentHashMap<>();

    @GetMapping("/")
    public String home() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {

        if (error != null) {
            model.addAttribute("error", "Invalid username or password!");
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully!");
        }

        return "auth/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new User());
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("user") User user,
                                      BindingResult bindingResult,
                                      @RequestParam("confirmPassword") String confirmPassword,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        if (!user.getPassword().equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match!");
            return "auth/register";
        }

        try {
            userService.registerUser(user.getUsername(), user.getEmail(), user.getPassword());
            redirectAttributes.addFlashAttribute("success", "Registration successful! Please login.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return "redirect:/login?logout";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    // API endpoint for forgot password
    @PostMapping("/api/auth/forgot-password")
    @ResponseBody
    public Map<String, Object> forgotPassword(@RequestBody Map<String, String> req) {
        try {
            String email = req.get("email");
            logger.info("=== FORGOT PASSWORD REQUEST ===");
            logger.info("Email: {}", email);
            
            if (email == null || email.trim().isEmpty()) {
                logger.warn("Email is empty or null");
                return Map.of("status", "error", "message", "Email is required");
            }

            // Check if user exists
            var userOpt = userService.userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                logger.info("User not found for email: {}", email);
                // Always show generic message for security
                return Map.of("status", "ok", "message", "If the email exists, a reset link has been sent.");
            }

            logger.info("User found for email: {}", email);

            // Generate reset token
            String token = UUID.randomUUID().toString();
            resetTokens.put(token, email);
            logger.info("Reset token generated: {}", token);

            // Create reset link
            String resetLink = "http://localhost:8080/reset-password?token=" + token;
            logger.info("Reset link created: {}", resetLink);
            
            // Send email
            String subject = "Weather Dashboard Password Reset";
            String body = "Hello,\n\nYou have requested to reset your password for Weather Dashboard.\n\n" +
                         "Click the following link to reset your password:\n" + resetLink + "\n\n" +
                         "This link will expire in 24 hours.\n\n" +
                         "If you didn't request this reset, please ignore this email.\n\n" +
                         "Best regards,\nWeather Dashboard Team";

            logger.info("Attempting to send email to: {}", email);
            logger.info("Email subject: {}", subject);
            logger.info("Email body length: {}", body.length());

            try {
                emailService.sendSimpleMail(email, subject, body);
                logger.info("Email sent successfully to: {}", email);
            } catch (Exception emailException) {
                logger.error("Failed to send email: {}", emailException.getMessage(), emailException);
                return Map.of("status", "error", "message", "Failed to send email: " + emailException.getMessage());
            }

            return Map.of("status", "ok", "message", "If the email exists, a reset link has been sent.");
            
        } catch (Exception e) {
            logger.error("Error in forgot password: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", "Failed to process password reset request: " + e.getMessage());
        }
    }

    // API endpoint for resetting password
    @PostMapping("/api/auth/reset-password")
    @ResponseBody
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> req) {
        try {
            String token = req.get("token");
            String newPassword = req.get("password");
            
            if (token == null || newPassword == null) {
                return Map.of("status", "error", "message", "Token and password are required");
            }

            if (newPassword.length() < 6) {
                return Map.of("status", "error", "message", "Password must be at least 6 characters long");
            }

            // Get email from token
            String email = resetTokens.get(token);
            if (email == null) {
                return Map.of("status", "error", "message", "Invalid or expired token");
            }

            // Find user
            var userOpt = userService.userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return Map.of("status", "error", "message", "User not found");
            }

            // Update password
            var user = userOpt.get();
            user.setPassword(userService.passwordEncoder.encode(newPassword));
            userService.userRepository.save(user);

            // Remove used token
            resetTokens.remove(token);

            return Map.of("status", "ok", "message", "Password reset successful");
            
        } catch (Exception e) {
            return Map.of("status", "error", "message", "Failed to reset password");
        }
    }
}
