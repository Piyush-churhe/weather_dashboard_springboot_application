package com.example.weatherdashboard.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UserPreferences {

    @NotBlank(message = "Temperature unit is required")
    @Pattern(regexp = "^(celsius|fahrenheit)$", message = "Temperature unit must be either 'celsius' or 'fahrenheit'")
    private String temperatureUnit = "celsius"; // celsius, fahrenheit

    @NotBlank(message = "Wind speed unit is required")
    @Pattern(regexp = "^(kmh|mph|ms)$", message = "Wind speed unit must be 'kmh', 'mph', or 'ms'")
    private String windSpeedUnit = "kmh"; // kmh, mph, ms

    @NotBlank(message = "Time format is required")
    @Pattern(regexp = "^(12h|24h)$", message = "Time format must be either '12h' or '24h'")
    private String timeFormat = "24h"; // 12h, 24h

    private boolean notifications = true;

    @NotBlank(message = "Theme is required")
    @Pattern(regexp = "^(light|dark)$", message = "Theme must be either 'light' or 'dark'")
    private String theme = "light"; // light, dark

    // Constructors
    public UserPreferences() {}

    public UserPreferences(String temperatureUnit, String windSpeedUnit, String timeFormat, boolean notifications, String theme) {
        this.temperatureUnit = temperatureUnit;
        this.windSpeedUnit = windSpeedUnit;
        this.timeFormat = timeFormat;
        this.notifications = notifications;
        this.theme = theme;
    }

    // Getters and Setters
    public String getTemperatureUnit() {
        return temperatureUnit;
    }

    public void setTemperatureUnit(String temperatureUnit) {
        this.temperatureUnit = temperatureUnit;
    }

    public String getWindSpeedUnit() {
        return windSpeedUnit;
    }

    public void setWindSpeedUnit(String windSpeedUnit) {
        this.windSpeedUnit = windSpeedUnit;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public boolean isNotifications() {
        return notifications;
    }

    public void setNotifications(boolean notifications) {
        this.notifications = notifications;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    // Additional utility methods
    @Override
    public String toString() {
        return "UserPreferences{" +
                "temperatureUnit='" + temperatureUnit + '\'' +
                ", windSpeedUnit='" + windSpeedUnit + '\'' +
                ", timeFormat='" + timeFormat + '\'' +
                ", notifications=" + notifications +
                ", theme='" + theme + '\'' +
                '}';
    }

    // Copy constructor for creating a copy of preferences
    public UserPreferences copy() {
        return new UserPreferences(this.temperatureUnit, this.windSpeedUnit, this.timeFormat, this.notifications, this.theme);
    }
}