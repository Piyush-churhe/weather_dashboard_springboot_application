package com.example.weatherdashboard.util;

public class TemperatureUtil {

    /**
     * Convert Celsius to Fahrenheit
     */
    public static double celsiusToFahrenheit(double celsius) {
        return (celsius * 9.0 / 5.0) + 32.0;
    }

    /**
     * Convert Fahrenheit to Celsius
     */
    public static double fahrenheitToCelsius(double fahrenheit) {
        return (fahrenheit - 32.0) * 5.0 / 9.0;
    }

    /**
     * Format temperature with unit conversion
     */
    public static String formatTemperature(double tempCelsius, String unit) {
        if ("fahrenheit".equals(unit)) {
            return String.format("%.0f°F", celsiusToFahrenheit(tempCelsius));
        } else {
            return String.format("%.0f°C", tempCelsius);
        }
    }

    /**
     * Convert wind speed from km/h to other units
     */
    public static double convertWindSpeed(double speedKmh, String unit) {
        switch (unit) {
            case "mph":
                return speedKmh * 0.621371; // km/h to mph
            case "ms":
                return speedKmh * 0.277778; // km/h to m/s
            default:
                return speedKmh; // keep as km/h
        }
    }

    /**
     * Format wind speed with unit conversion
     */
    public static String formatWindSpeed(double speedKmh, String unit) {
        double convertedSpeed = convertWindSpeed(speedKmh, unit);

        switch (unit) {
            case "mph":
                return String.format("%.1f mph", convertedSpeed);
            case "ms":
                return String.format("%.1f m/s", convertedSpeed);
            default:
                return String.format("%.1f km/h", convertedSpeed);
        }
    }

    /**
     * Convert pressure from hPa to other units
     */
    public static double convertPressure(double pressureHPa, String unit) {
        switch (unit) {
            case "inHg":
                return pressureHPa * 0.02953; // hPa to inHg
            case "mmHg":
                return pressureHPa * 0.75006; // hPa to mmHg
            case "atm":
                return pressureHPa * 0.00098692; // hPa to atm
            default:
                return pressureHPa; // keep as hPa
        }
    }

    /**
     * Format pressure with unit conversion
     */
    public static String formatPressure(double pressureHPa, String unit) {
        double convertedPressure = convertPressure(pressureHPa, unit);

        switch (unit) {
            case "inHg":
                return String.format("%.2f inHg", convertedPressure);
            case "mmHg":
                return String.format("%.0f mmHg", convertedPressure);
            case "atm":
                return String.format("%.3f atm", convertedPressure);
            default:
                return String.format("%.0f hPa", convertedPressure);
        }
    }

    /**
     * Convert visibility from meters to other units
     */
    public static double convertVisibility(double visibilityMeters, String unit) {
        switch (unit) {
            case "miles":
                return visibilityMeters * 0.000621371; // meters to miles
            case "feet":
                return visibilityMeters * 3.28084; // meters to feet
            case "km":
                return visibilityMeters / 1000; // meters to kilometers
            default:
                return visibilityMeters; // keep as meters
        }
    }

    /**
     * Format visibility with unit conversion
     */
    public static String formatVisibility(double visibilityMeters, String unit) {
        double convertedVisibility = convertVisibility(visibilityMeters, unit);

        switch (unit) {
            case "miles":
                return String.format("%.1f mi", convertedVisibility);
            case "feet":
                return String.format("%.0f ft", convertedVisibility);
            case "km":
                return String.format("%.1f km", convertedVisibility);
            default:
                return String.format("%.0f m", convertedVisibility);
        }
    }

    /**
     * Calculate heat index (feels like temperature)
     */
    public static double calculateHeatIndex(double temperature, double humidity) {
        // Convert to Fahrenheit for calculation if needed
        double tempF = temperature;

        // Simple heat index calculation
        if (tempF >= 80 && humidity >= 40) {
            double heatIndex = -42.379 + 2.04901523 * tempF + 10.14333127 * humidity
                    - 0.22475541 * tempF * humidity - 6.83783e-03 * tempF * tempF
                    - 5.481717e-02 * humidity * humidity + 1.22874e-03 * tempF * tempF * humidity
                    + 8.5282e-04 * tempF * humidity * humidity - 1.99e-06 * tempF * tempF * humidity * humidity;

            return heatIndex;
        }

        return tempF; // Return original temperature if conditions don't warrant heat index
    }

    /**
     * Calculate wind chill
     */
    public static double calculateWindChill(double temperature, double windSpeedMph) {
        // Wind chill formula (temperature in Fahrenheit, wind speed in mph)
        if (temperature <= 50 && windSpeedMph > 3) {
            return 35.74 + 0.6215 * temperature - 35.75 * Math.pow(windSpeedMph, 0.16)
                    + 0.4275 * temperature * Math.pow(windSpeedMph, 0.16);
        }

        return temperature; // Return original temperature if conditions don't warrant wind chill
    }

    /**
     * Get comfort level description based on temperature and humidity
     */
    public static String getComfortLevel(double temperature, double humidity) {
        // Simple comfort index
        if (temperature >= 20 && temperature <= 26 && humidity >= 30 && humidity <= 60) {
            return "Comfortable";
        } else if (temperature > 30 || humidity > 70) {
            return "Hot & Humid";
        } else if (temperature < 10) {
            return "Cold";
        } else if (humidity > 80) {
            return "Very Humid";
        } else if (humidity < 30) {
            return "Dry";
        } else {
            return "Moderate";
        }
    }
}