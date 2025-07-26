package com.example.weatherdashboard.controller;

import com.example.weatherdashboard.model.User;
import com.example.weatherdashboard.service.UserService;
import com.example.weatherdashboard.service.WeatherService;
import com.example.weatherdashboard.util.TemperatureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/dashboard")
public class WeatherController {

    private static final Logger logger = LoggerFactory.getLogger(WeatherController.class);

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private UserService userService;

    @GetMapping
    public String dashboard(Model model) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            User user = null;
            String githubLogin = null;

            // Robust OAuth2 handling for all providers (same as profile page)
            if (auth instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauthToken) {
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
                        logger.info("Found GitHub OAuth user by pseudo-email in dashboard: {}", pseudoEmail);
                    }
                }
            }

            // 4. Try by principal as username
            if (user == null && username != null && !username.contains("@")) {
                user = userService.userRepository.findByUsername(username).orElse(null);
            }
            // 5. Try by principal as email
            if (user == null && username != null && username.contains("@")) {
                user = userService.userRepository.findByEmail(username).orElse(null);
            }
            // 6. FINAL fallback: If OAuth2 login is present, try login@github.com as email
            if (user == null && githubLogin != null) {
                String pseudoEmail = githubLogin + "@github.com";
                user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                if (user != null) {
                    logger.info("Final fallback: Found GitHub OAuth user by pseudo-email in dashboard: {}", pseudoEmail);
                }
            }

            model.addAttribute("user", user);

            // Get user preferences (with safe fallback)
            com.example.weatherdashboard.model.UserPreferences userPreferences = null;
            if (user != null && user.getPreferences() != null) {
                userPreferences = user.getPreferences();
            }
            model.addAttribute("userPreferences", userPreferences);

            // Try to load default weather (Mumbai) with proper error handling
            try {
                Map<String, Object> weatherData = weatherService.getCurrentWeather("Mumbai");

                // Apply user preferences to weather data if available
                if (userPreferences != null) {
                    weatherData = applyUserPreferencesToWeatherData(weatherData, userPreferences);
                } else {
                    // Ensure basic display fields are set even without preferences
                    weatherData = ensureBasicWeatherFields(weatherData);
                }

                model.addAttribute("currentWeather", weatherData);
                model.addAttribute("currentCity", "Mumbai");

                // Try to get hourly forecast
                try {
                    Map<String, Object> hourlyForecast = weatherService.getHourlyForecast("Mumbai");
                    if (userPreferences != null) {
                        hourlyForecast = applyUserPreferencesToHourlyForecast(hourlyForecast, userPreferences);
                    } else {
                        hourlyForecast = ensureBasicHourlyFields(hourlyForecast);
                    }
                    model.addAttribute("hourlyForecast", hourlyForecast);
                } catch (Exception e) {
                    logger.error("Error loading hourly forecast", e);
                }

                // Try to get weather alerts
                try {
                    List<Map<String, Object>> alerts = weatherService.getWeatherAlerts("Mumbai");
                    model.addAttribute("weatherAlerts", alerts);
                    model.addAttribute("hasAlerts", !alerts.isEmpty());
                } catch (Exception e) {
                    logger.error("Error loading weather alerts", e);
                    model.addAttribute("hasAlerts", false);
                }

            } catch (Exception e) {
                logger.error("Error loading default weather", e);
                model.addAttribute("error", "Unable to load default weather data");
            }

        } catch (Exception e) {
            logger.error("Error loading dashboard", e);
            model.addAttribute("error", "Error loading dashboard: " + e.getMessage());
        }

        return "dashboard/weather-dashboard";
    }

    @GetMapping("/weather/{city}")
    public String getWeatherByCity(@PathVariable String city, Model model) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            User user = null;
            String githubLogin = null;

            // Robust OAuth2 handling for all providers (same as dashboard method)
            if (auth instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauthToken) {
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
                        logger.info("Found GitHub OAuth user by pseudo-email in weather page: {}", pseudoEmail);
                    }
                }
            }

            // 4. Try by principal as username
            if (user == null && username != null && !username.contains("@")) {
                user = userService.userRepository.findByUsername(username).orElse(null);
            }
            // 5. Try by principal as email
            if (user == null && username != null && username.contains("@")) {
                user = userService.userRepository.findByEmail(username).orElse(null);
            }
            // 6. FINAL fallback: If OAuth2 login is present, try login@github.com as email
            if (user == null && githubLogin != null) {
                String pseudoEmail = githubLogin + "@github.com";
                user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                if (user != null) {
                    logger.info("Final fallback: Found GitHub OAuth user by pseudo-email in weather page: {}", pseudoEmail);
                }
            }

            model.addAttribute("user", user);

            // Get user preferences (with safe fallback)
            com.example.weatherdashboard.model.UserPreferences userPreferences = null;
            if (user != null && user.getPreferences() != null) {
                userPreferences = user.getPreferences();
            }

            // Get current weather
            Map<String, Object> weatherData = weatherService.getCurrentWeather(city);

            // Apply user preferences to weather data
            if (userPreferences != null) {
                weatherData = applyUserPreferencesToWeatherData(weatherData, userPreferences);
            } else {
                weatherData = ensureBasicWeatherFields(weatherData);
            }

            model.addAttribute("currentWeather", weatherData);
            model.addAttribute("currentCity", city);

            // Get hourly forecast
            try {
                Map<String, Object> hourlyForecast = weatherService.getHourlyForecast(city);

                // Apply user preferences to hourly forecast
                if (userPreferences != null) {
                    hourlyForecast = applyUserPreferencesToHourlyForecast(hourlyForecast, userPreferences);
                } else {
                    hourlyForecast = ensureBasicHourlyFields(hourlyForecast);
                }

                model.addAttribute("hourlyForecast", hourlyForecast);
            } catch (Exception e) {
                logger.error("Error loading hourly forecast for {}: {}", city, e.getMessage());
                model.addAttribute("forecastError", "Unable to load hourly forecast");
            }

            // Get weather alerts
            try {
                List<Map<String, Object>> alerts = weatherService.getWeatherAlerts(city);
                model.addAttribute("weatherAlerts", alerts);
                model.addAttribute("hasAlerts", !alerts.isEmpty());
            } catch (Exception e) {
                logger.error("Error loading weather alerts for {}: {}", city, e.getMessage());
                model.addAttribute("alertsError", "Unable to load weather alerts");
                model.addAttribute("hasAlerts", false);
            }

        } catch (Exception e) {
            logger.error("Error getting weather for city: {}", city, e);
            model.addAttribute("error", "Error getting weather for " + city + ": " + e.getMessage());
        }

        return "dashboard/weather-dashboard";
    }

    // ============================================
    // OTHER ENDPOINTS
    // ============================================

    @GetMapping("/api/forecast/{city}")
    @ResponseBody
    public Map<String, Object> getHourlyForecastApi(@PathVariable String city) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            if (username.contains("@")) {
                User oauthUser = userService.userRepository.findByEmail(username).orElse(null);
                if (oauthUser != null) {
                    username = oauthUser.getUsername();
                }
            }

            User user = userService.findByUsername(username).orElse(null);
            if (user == null && auth.getName().contains("@")) {
                user = userService.userRepository.findByEmail(auth.getName()).orElse(null);
            }

            Map<String, Object> hourlyForecast = weatherService.getHourlyForecast(city);

            // Apply user preferences
            if (user != null && user.getPreferences() != null) {
                hourlyForecast = applyUserPreferencesToHourlyForecast(hourlyForecast, user.getPreferences());
            } else {
                hourlyForecast = ensureBasicHourlyFields(hourlyForecast);
            }

            return hourlyForecast;
        } catch (Exception e) {
            logger.error("Error getting hourly forecast API for {}: {}", city, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @GetMapping("/api/alerts/{city}")
    @ResponseBody
    public List<Map<String, Object>> getWeatherAlertsApi(@PathVariable String city) {
        try {
            return weatherService.getWeatherAlerts(city);
        } catch (Exception e) {
            logger.error("Error getting weather alerts API for {}: {}", city, e.getMessage());
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, Object>> searchCities(@RequestParam String query) {
        try {
            return weatherService.searchCities(query);
        } catch (Exception e) {
            logger.error("Error searching cities", e);
            return List.of();
        }
    }

    @GetMapping("/compare")
    public String comparePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        if (username.contains("@")) {
            User oauthUser = userService.userRepository.findByEmail(username).orElse(null);
            if (oauthUser != null) {
                username = oauthUser.getUsername();
            }
        }

        User user = userService.findByUsername(username).orElse(null);
        if (user == null && auth.getName().contains("@")) {
            user = userService.userRepository.findByEmail(auth.getName()).orElse(null);
        }

        model.addAttribute("user", user);

        if (user != null) {
            model.addAttribute("userPreferences", user.getPreferences());
        }

        return "dashboard/compare";
    }

    @GetMapping("/forecast/{city}")
    public String detailedForecast(@PathVariable String city, Model model) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            logger.info("=== FORECAST ACCESS ATTEMPT ===");
            logger.info("Auth username: {}", username);
            logger.info("Auth authorities: {}", auth.getAuthorities());

            User user = null;
            String githubLogin = null;

            // Robust OAuth2 handling for all providers (same as dashboard method)
            if (auth instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauthToken) {
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
                        logger.info("Found GitHub OAuth user by pseudo-email in forecast: {}", pseudoEmail);
                    }
                }
            }

            // 4. Try by principal as username
            if (user == null && username != null && !username.contains("@")) {
                user = userService.userRepository.findByUsername(username).orElse(null);
            }
            // 5. Try by principal as email
            if (user == null && username != null && username.contains("@")) {
                user = userService.userRepository.findByEmail(username).orElse(null);
            }
            // 6. FINAL fallback: If OAuth2 login is present, try login@github.com as email
            if (user == null && githubLogin != null) {
                String pseudoEmail = githubLogin + "@github.com";
                user = userService.userRepository.findByEmail(pseudoEmail).orElse(null);
                if (user != null) {
                    logger.info("Final fallback: Found GitHub OAuth user by pseudo-email in forecast: {}", pseudoEmail);
                }
            }

            model.addAttribute("user", user);

            // Check if user has premium or admin access
            boolean hasPremiumAccess = false;
            if (user != null) {
                hasPremiumAccess = user.getRoles().contains("PREMIUM") || user.getRoles().contains("ADMIN");
                logger.info("User {} roles: {}, has premium access: {}", user.getUsername(), user.getRoles(), hasPremiumAccess);
            } else {
                logger.warn("User not found in database for username/email: {}", username);
            }

            if (!hasPremiumAccess) {
                logger.warn("User {} tried to access forecast page without premium access. Roles: {}",
                        username, user != null ? user.getRoles() : "user not found");
                model.addAttribute("error", "You need to upgrade to Premium to access detailed forecasts.");
                model.addAttribute("needsUpgrade", true);
                return "dashboard/access-denied";
            }

            logger.info("User {} granted access to forecast page", username);

            if (user != null && user.getPreferences() != null) {
                model.addAttribute("userPreferences", user.getPreferences());
            }

            // Get current weather
            Map<String, Object> currentWeather = weatherService.getCurrentWeather(city);
            if (user != null && user.getPreferences() != null) {
                currentWeather = applyUserPreferencesToWeatherData(currentWeather, user.getPreferences());
            } else {
                currentWeather = ensureBasicWeatherFields(currentWeather);
            }
            model.addAttribute("currentWeather", currentWeather);
            model.addAttribute("currentCity", city);

            // Get hourly forecast
            Map<String, Object> hourlyForecast = weatherService.getHourlyForecast(city);
            if (user != null && user.getPreferences() != null) {
                hourlyForecast = applyUserPreferencesToHourlyForecast(hourlyForecast, user.getPreferences());
            } else {
                hourlyForecast = ensureBasicHourlyFields(hourlyForecast);
            }
            model.addAttribute("hourlyForecast", hourlyForecast);

            // Get weather alerts
            List<Map<String, Object>> alerts = weatherService.getWeatherAlerts(city);
            model.addAttribute("weatherAlerts", alerts);
            model.addAttribute("hasAlerts", !alerts.isEmpty());

        } catch (Exception e) {
            logger.error("Error loading detailed forecast for {}: {}", city, e.getMessage());
            model.addAttribute("error", "Error loading forecast: " + e.getMessage());
        }

        return "dashboard/detailed-forecast";
    }

    @GetMapping("/admin")
    public String adminDashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        if (username.contains("@")) {
            User oauthUser = userService.userRepository.findByEmail(username).orElse(null);
            if (oauthUser != null) {
                username = oauthUser.getUsername();
            }
        }

        User user = userService.findByUsername(username).orElse(null);
        if (user == null && auth.getName().contains("@")) {
            user = userService.userRepository.findByEmail(auth.getName()).orElse(null);
        }

        if (user == null || !user.getRoles().contains("ADMIN")) {
            return "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        return "dashboard/admin";
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    /**
     * Ensure basic weather fields are present for template safety (without user preferences)
     */
    private Map<String, Object> ensureBasicWeatherFields(Map<String, Object> weatherData) {
        try {
            if (weatherData.containsKey("main")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> main = (Map<String, Object>) weatherData.get("main");

                // Ensure temperature display fields
                if (main.containsKey("temp") && !main.containsKey("temp_display")) {
                    double tempCelsius = ((Number) main.get("temp")).doubleValue();
                    main.put("temp_display", String.format("%.0f°C", tempCelsius));
                    main.put("temp_unit", "°C");
                    main.put("temp_numeric", tempCelsius);
                }

                if (main.containsKey("feels_like") && !main.containsKey("feels_like_display")) {
                    double feelsLikeCelsius = ((Number) main.get("feels_like")).doubleValue();
                    main.put("feels_like_display", String.format("%.0f°C", feelsLikeCelsius));
                    main.put("feels_like_numeric", feelsLikeCelsius);
                }
            }

            // Ensure wind fields are present
            if (weatherData.containsKey("wind")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> wind = (Map<String, Object>) weatherData.get("wind");

                if (wind.containsKey("speed") && !wind.containsKey("speed_display")) {
                    double speedMps = ((Number) wind.get("speed")).doubleValue();
                    double speedKmh = speedMps * 3.6; // m/s to km/h
                    wind.put("speed_display", String.format("%.1f km/h", speedKmh));
                    wind.put("speed_numeric", speedKmh);
                }
            } else {
                // Create empty wind object if not present
                Map<String, Object> wind = new HashMap<>();
                wind.put("speed", 0);
                wind.put("speed_display", "0 km/h");
                wind.put("speed_numeric", 0);
                weatherData.put("wind", wind);
            }

        } catch (Exception e) {
            logger.error("Error ensuring basic weather fields", e);
        }

        return weatherData;
    }

    /**
     * Ensure basic hourly forecast fields are present
     */
    private Map<String, Object> ensureBasicHourlyFields(Map<String, Object> hourlyForecast) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hourlyData = (List<Map<String, Object>>) hourlyForecast.get("hourly");

            if (hourlyData != null) {
                for (Map<String, Object> hour : hourlyData) {
                    // Ensure temperature display
                    if (hour.containsKey("temp") && !hour.containsKey("temp_display")) {
                        double tempCelsius = ((Number) hour.get("temp")).doubleValue();
                        hour.put("temp_display", String.format("%.0f°C", tempCelsius));
                        hour.put("temp_numeric", tempCelsius);
                    }

                    // Ensure feels like display
                    if (hour.containsKey("feels_like") && !hour.containsKey("feels_like_display")) {
                        double feelsLikeCelsius = ((Number) hour.get("feels_like")).doubleValue();
                        hour.put("feels_like_display", String.format("%.0f°C", feelsLikeCelsius));
                    }

                    // Ensure wind speed display
                    if (hour.containsKey("wind_speed") && !hour.containsKey("wind_speed_display")) {
                        double speedMps = ((Number) hour.get("wind_speed")).doubleValue();
                        double speedKmh = speedMps * 3.6; // Convert m/s to km/h
                        hour.put("wind_speed_display", String.format("%.1f km/h", speedKmh));
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error ensuring basic hourly fields", e);
        }

        return hourlyForecast;
    }

    /**
     * Apply user preferences to hourly forecast data
     */
    private Map<String, Object> applyUserPreferencesToHourlyForecast(
            Map<String, Object> hourlyForecast,
            com.example.weatherdashboard.model.UserPreferences preferences) {

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hourlyData = (List<Map<String, Object>>) hourlyForecast.get("hourly");

            if (hourlyData != null) {
                for (Map<String, Object> hour : hourlyData) {
                    // Convert temperature
                    if (hour.containsKey("temp") && "fahrenheit".equals(preferences.getTemperatureUnit())) {
                        double tempCelsius = ((Number) hour.get("temp")).doubleValue();
                        hour.put("temp_display", TemperatureUtil.formatTemperature(tempCelsius, "fahrenheit"));
                        hour.put("temp_numeric", TemperatureUtil.celsiusToFahrenheit(tempCelsius));
                    } else if (hour.containsKey("temp")) {
                        double tempCelsius = ((Number) hour.get("temp")).doubleValue();
                        hour.put("temp_display", TemperatureUtil.formatTemperature(tempCelsius, "celsius"));
                        hour.put("temp_numeric", tempCelsius);
                    }

                    // Convert feels like temperature
                    if (hour.containsKey("feels_like") && "fahrenheit".equals(preferences.getTemperatureUnit())) {
                        double feelsLikeCelsius = ((Number) hour.get("feels_like")).doubleValue();
                        hour.put("feels_like_display", TemperatureUtil.formatTemperature(feelsLikeCelsius, "fahrenheit"));
                    } else if (hour.containsKey("feels_like")) {
                        double feelsLikeCelsius = ((Number) hour.get("feels_like")).doubleValue();
                        hour.put("feels_like_display", TemperatureUtil.formatTemperature(feelsLikeCelsius, "celsius"));
                    }

                    // Convert wind speed
                    if (hour.containsKey("wind_speed")) {
                        double speedMps = ((Number) hour.get("wind_speed")).doubleValue();
                        double speedKmh = speedMps * 3.6; // Convert m/s to km/h
                        hour.put("wind_speed_display", TemperatureUtil.formatWindSpeed(speedKmh, preferences.getWindSpeedUnit()));
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error applying user preferences to hourly forecast", e);
        }

        return hourlyForecast;
    }

    /**
     * Apply user preferences to weather data for display
     */
    private Map<String, Object> applyUserPreferencesToWeatherData(Map<String, Object> weatherData,
                                                                  com.example.weatherdashboard.model.UserPreferences preferences) {
        try {
            if (weatherData.containsKey("main")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> main = (Map<String, Object>) weatherData.get("main");

                // Convert temperature if user prefers Fahrenheit
                if ("fahrenheit".equals(preferences.getTemperatureUnit())) {
                    if (main.containsKey("temp")) {
                        double tempCelsius = ((Number) main.get("temp")).doubleValue();
                        main.put("temp_display", TemperatureUtil.formatTemperature(tempCelsius, "fahrenheit"));
                        main.put("temp_unit", "°F");
                        main.put("temp_numeric", TemperatureUtil.celsiusToFahrenheit(tempCelsius));
                    }

                    if (main.containsKey("feels_like")) {
                        double feelsLikeCelsius = ((Number) main.get("feels_like")).doubleValue();
                        main.put("feels_like_display", TemperatureUtil.formatTemperature(feelsLikeCelsius, "fahrenheit"));
                        main.put("feels_like_numeric", TemperatureUtil.celsiusToFahrenheit(feelsLikeCelsius));
                    }
                } else {
                    // Default Celsius
                    if (main.containsKey("temp")) {
                        double tempCelsius = ((Number) main.get("temp")).doubleValue();
                        main.put("temp_display", TemperatureUtil.formatTemperature(tempCelsius, "celsius"));
                        main.put("temp_unit", "°C");
                        main.put("temp_numeric", tempCelsius);
                    }

                    if (main.containsKey("feels_like")) {
                        double feelsLikeCelsius = ((Number) main.get("feels_like")).doubleValue();
                        main.put("feels_like_display", TemperatureUtil.formatTemperature(feelsLikeCelsius, "celsius"));
                        main.put("feels_like_numeric", feelsLikeCelsius);
                    }
                }
            }

            // Convert wind speed based on user preference
            if (weatherData.containsKey("wind")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> wind = (Map<String, Object>) weatherData.get("wind");

                if (wind.containsKey("speed")) {
                    double speedKmh = ((Number) wind.get("speed")).doubleValue() * 3.6; // m/s to km/h conversion
                    wind.put("speed_display", TemperatureUtil.formatWindSpeed(speedKmh, preferences.getWindSpeedUnit()));
                    wind.put("speed_numeric", TemperatureUtil.convertWindSpeed(speedKmh, preferences.getWindSpeedUnit()));
                }
            }

        } catch (Exception e) {
            logger.error("Error applying user preferences to weather data", e);
        }

        return weatherData;
    }
}