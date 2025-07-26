package com.example.weatherdashboard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.base-url}")
    private String baseUrl;

    @Value("${weather.api.geocoding-url}")
    private String geocodingUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get current weather for a city
     */
    public Map<String, Object> getCurrentWeather(String city) {
        try {
            String url = String.format("%s/weather?q=%s&appid=%s&units=metric",
                    baseUrl, city, apiKey);

            logger.info("Fetching current weather for: {}", city);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode jsonNode = objectMapper.readTree(response);
            return objectMapper.convertValue(jsonNode, Map.class);

        } catch (HttpClientErrorException e) {
            logger.error("HTTP error getting weather for {}: {}", city, e.getMessage());
            throw new RuntimeException("City not found: " + city);
        } catch (Exception e) {
            logger.error("Error getting weather for {}: {}", city, e.getMessage());
            throw new RuntimeException("Error fetching weather data: " + e.getMessage());
        }
    }

    /**
     * Get hourly forecast for the next 48 hours
     */
    public Map<String, Object> getHourlyForecast(String city) {
        try {
            // First, get coordinates for the city
            Map<String, Object> coordinates = getCityCoordinates(city);
            double lat = (Double) coordinates.get("lat");
            double lon = (Double) coordinates.get("lon");

            return getHourlyForecastByCoordinates(lat, lon);

        } catch (Exception e) {
            logger.error("Error getting hourly forecast for {}: {}", city, e.getMessage());
            throw new RuntimeException("Error fetching hourly forecast: " + e.getMessage());
        }
    }

    /**
     * Get hourly forecast by coordinates
     */
    public Map<String, Object> getHourlyForecastByCoordinates(double lat, double lon) {
        try {
            String url = String.format("%s/forecast?lat=%f&lon=%f&appid=%s&units=metric",
                    baseUrl, lat, lon, apiKey);

            logger.info("Fetching hourly forecast for coordinates: {}, {}", lat, lon);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> forecastData = objectMapper.convertValue(jsonNode, Map.class);

            // Process and format hourly data
            return processHourlyForecast(forecastData);

        } catch (Exception e) {
            logger.error("Error getting hourly forecast for coordinates {}, {}: {}",
                    lat, lon, e.getMessage());
            throw new RuntimeException("Error fetching hourly forecast: " + e.getMessage());
        }
    }

    /**
     * Get weather alerts for a city
     */
    public List<Map<String, Object>> getWeatherAlerts(String city) {
        try {
            // First, get coordinates for the city
            Map<String, Object> coordinates = getCityCoordinates(city);
            double lat = (Double) coordinates.get("lat");
            double lon = (Double) coordinates.get("lon");

            return getWeatherAlertsByCoordinates(lat, lon);

        } catch (Exception e) {
            logger.error("Error getting weather alerts for {}: {}", city, e.getMessage());
            return new ArrayList<>(); // Return empty list instead of throwing exception
        }
    }

    /**
     * Get weather alerts by coordinates using One Call API
     */
    public List<Map<String, Object>> getWeatherAlertsByCoordinates(double lat, double lon) {
        try {
            // Use One Call API 3.0 for alerts (requires subscription, fallback to basic alerts)
            String url = String.format("https://api.openweathermap.org/data/3.0/onecall?lat=%f&lon=%f&appid=%s&exclude=minutely,daily",
                    lat, lon, apiKey);

            logger.info("Fetching weather alerts for coordinates: {}, {}", lat, lon);

            try {
                String response = restTemplate.getForObject(url, String.class);
                JsonNode jsonNode = objectMapper.readTree(response);

                if (jsonNode.has("alerts")) {
                    JsonNode alertsNode = jsonNode.get("alerts");
                    List<Map<String, Object>> alerts = new ArrayList<>();

                    for (JsonNode alert : alertsNode) {
                        Map<String, Object> alertData = objectMapper.convertValue(alert, Map.class);
                        alerts.add(processAlert(alertData));
                    }

                    return alerts;
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 401) {
                    logger.warn("One Call API requires subscription, falling back to basic alerts");
                    return getBasicWeatherAlerts(lat, lon);
                }
                throw e;
            }

            return new ArrayList<>();

        } catch (Exception e) {
            logger.error("Error getting weather alerts for coordinates {}, {}: {}",
                    lat, lon, e.getMessage());
            return getBasicWeatherAlerts(lat, lon); // Fallback to basic alerts
        }
    }

    /**
     * Get basic weather alerts based on current conditions
     */
    private List<Map<String, Object>> getBasicWeatherAlerts(double lat, double lon) {
        List<Map<String, Object>> alerts = new ArrayList<>();

        try {
            // Get current weather to check for severe conditions
            String url = String.format("%s/weather?lat=%f&lon=%f&appid=%s&units=metric",
                    baseUrl, lat, lon, apiKey);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode weatherData = objectMapper.readTree(response);

            // Create alerts based on severe weather conditions
            if (weatherData.has("main")) {
                JsonNode main = weatherData.get("main");
                double temp = main.get("temp").asDouble();
                double feelsLike = main.get("feels_like").asDouble();
                int humidity = main.get("humidity").asInt();

                // Temperature alerts
                if (temp > 40) {
                    alerts.add(createAlert("Extreme Heat Warning",
                            "Extremely high temperatures detected. Stay hydrated and avoid prolonged sun exposure.",
                            "warning", "heat"));
                } else if (temp < -10) {
                    alerts.add(createAlert("Extreme Cold Warning",
                            "Extremely low temperatures detected. Dress warmly and limit outdoor exposure.",
                            "warning", "cold"));
                }

                // Heat index alert
                if (feelsLike - temp > 10) {
                    alerts.add(createAlert("High Heat Index Alert",
                            "Temperature feels significantly higher due to humidity. Take extra precautions.",
                            "advisory", "heat"));
                }

                // High humidity alert
                if (humidity > 90) {
                    alerts.add(createAlert("High Humidity Advisory",
                            "Very high humidity levels detected. Stay hydrated and take frequent breaks.",
                            "advisory", "humidity"));
                }
            }

            // Wind alerts
            if (weatherData.has("wind")) {
                JsonNode wind = weatherData.get("wind");
                double windSpeed = wind.get("speed").asDouble() * 3.6; // Convert m/s to km/h

                if (windSpeed > 60) {
                    alerts.add(createAlert("High Wind Warning",
                            "Strong winds detected. Secure loose objects and avoid outdoor activities.",
                            "warning", "wind"));
                } else if (windSpeed > 40) {
                    alerts.add(createAlert("Wind Advisory",
                            "Moderate to strong winds expected. Exercise caution when outdoors.",
                            "advisory", "wind"));
                }
            }

            // Weather condition alerts
            if (weatherData.has("weather")) {
                JsonNode weather = weatherData.get("weather").get(0);
                String mainCondition = weather.get("main").asText().toLowerCase();

                switch (mainCondition) {
                    case "thunderstorm":
                        alerts.add(createAlert("Thunderstorm Alert",
                                "Thunderstorm activity in the area. Seek shelter indoors and avoid open areas.",
                                "warning", "storm"));
                        break;
                    case "snow":
                        alerts.add(createAlert("Snow Advisory",
                                "Snow conditions detected. Drive carefully and dress warmly.",
                                "advisory", "snow"));
                        break;
                    case "fog":
                    case "mist":
                        alerts.add(createAlert("Visibility Advisory",
                                "Reduced visibility due to fog or mist. Drive carefully and use headlights.",
                                "advisory", "fog"));
                        break;
                }
            }

        } catch (Exception e) {
            logger.error("Error creating basic weather alerts: {}", e.getMessage());
        }

        return alerts;
    }

    /**
     * Create a weather alert object
     */
    private Map<String, Object> createAlert(String title, String description, String severity, String type) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("title", title);
        alert.put("description", description);
        alert.put("severity", severity); // warning, advisory, watch
        alert.put("type", type); // heat, cold, wind, storm, etc.
        alert.put("start", System.currentTimeMillis() / 1000); // Current time
        alert.put("end", (System.currentTimeMillis() / 1000) + (24 * 3600)); // 24 hours from now
        alert.put("source", "Weather Dashboard System");

        return alert;
    }

    /**
     * Process alert data from API
     */
    private Map<String, Object> processAlert(Map<String, Object> alertData) {
        Map<String, Object> processedAlert = new HashMap<>();

        processedAlert.put("title", alertData.getOrDefault("event", "Weather Alert"));
        processedAlert.put("description", alertData.getOrDefault("description", ""));
        processedAlert.put("start", alertData.getOrDefault("start", 0));
        processedAlert.put("end", alertData.getOrDefault("end", 0));
        processedAlert.put("source", alertData.getOrDefault("sender_name", "Weather Service"));

        // Determine severity based on event type
        String event = (String) alertData.getOrDefault("event", "");
        String severity = determineSeverity(event);
        processedAlert.put("severity", severity);

        return processedAlert;
    }

    /**
     * Determine alert severity based on event type
     */
    private String determineSeverity(String event) {
        event = event.toLowerCase();

        if (event.contains("warning") || event.contains("emergency")) {
            return "warning";
        } else if (event.contains("watch")) {
            return "watch";
        } else {
            return "advisory";
        }
    }

    /**
     * Process hourly forecast data
     */
    private Map<String, Object> processHourlyForecast(Map<String, Object> forecastData) {
        Map<String, Object> processedData = new HashMap<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) forecastData.get("list");

        List<Map<String, Object>> hourlyData = new ArrayList<>();

        for (int i = 0; i < Math.min(list.size(), 16); i++) { // Next 48 hours (16 * 3-hour intervals)
            Map<String, Object> item = list.get(i);
            Map<String, Object> hourlyItem = new HashMap<>();

            // Time
            long timestamp = ((Number) item.get("dt")).longValue();
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());

            hourlyItem.put("dt", timestamp);
            hourlyItem.put("datetime", dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            hourlyItem.put("hour", dateTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            hourlyItem.put("day", dateTime.format(DateTimeFormatter.ofPattern("EEE")));

            // Weather data
            @SuppressWarnings("unchecked")
            Map<String, Object> main = (Map<String, Object>) item.get("main");
            hourlyItem.put("temp", main.get("temp"));
            hourlyItem.put("feels_like", main.get("feels_like"));
            hourlyItem.put("humidity", main.get("humidity"));
            hourlyItem.put("pressure", main.get("pressure"));

            // Weather description
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> weather = (List<Map<String, Object>>) item.get("weather");
            if (!weather.isEmpty()) {
                Map<String, Object> weatherInfo = weather.get(0);
                hourlyItem.put("weather", weatherInfo.get("main"));
                hourlyItem.put("description", weatherInfo.get("description"));
                hourlyItem.put("icon", weatherInfo.get("icon"));
            }

            // Wind
            if (item.containsKey("wind")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> wind = (Map<String, Object>) item.get("wind");
                hourlyItem.put("wind_speed", wind.get("speed"));
                hourlyItem.put("wind_deg", wind.getOrDefault("deg", 0));
            }

            // Rain/Snow
            if (item.containsKey("rain")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rain = (Map<String, Object>) item.get("rain");
                hourlyItem.put("rain", rain.getOrDefault("3h", 0));
            } else {
                hourlyItem.put("rain", 0);
            }

            if (item.containsKey("snow")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> snow = (Map<String, Object>) item.get("snow");
                hourlyItem.put("snow", snow.getOrDefault("3h", 0));
            } else {
                hourlyItem.put("snow", 0);
            }

            // Precipitation probability
            hourlyItem.put("pop", ((Number) item.getOrDefault("pop", 0)).doubleValue() * 100);

            hourlyData.add(hourlyItem);
        }

        processedData.put("hourly", hourlyData);
        processedData.put("city", forecastData.get("city"));

        return processedData;
    }

    /**
     * Get city coordinates for geocoding
     */
    private Map<String, Object> getCityCoordinates(String city) {
        try {
            String url = String.format("%s/direct?q=%s&limit=1&appid=%s",
                    geocodingUrl, city, apiKey);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonArray = objectMapper.readTree(response);

            if (jsonArray.isArray() && jsonArray.size() > 0) {
                JsonNode location = jsonArray.get(0);
                Map<String, Object> coordinates = new HashMap<>();
                coordinates.put("lat", location.get("lat").asDouble());
                coordinates.put("lon", location.get("lon").asDouble());
                coordinates.put("name", location.get("name").asText());
                coordinates.put("country", location.get("country").asText());
                return coordinates;
            } else {
                throw new RuntimeException("City not found: " + city);
            }

        } catch (Exception e) {
            logger.error("Error getting coordinates for city {}: {}", city, e.getMessage());
            throw new RuntimeException("Error getting city coordinates: " + e.getMessage());
        }
    }

    /**
     * Search for cities (existing method)
     */
    public List<Map<String, Object>> searchCities(String query) {
        try {
            String url = String.format("%s/direct?q=%s&limit=5&appid=%s",
                    geocodingUrl, query, apiKey);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonArray = objectMapper.readTree(response);

            List<Map<String, Object>> cities = new ArrayList<>();

            for (JsonNode city : jsonArray) {
                Map<String, Object> cityData = new HashMap<>();
                cityData.put("name", city.get("name").asText());
                cityData.put("country", city.get("country").asText());
                cityData.put("lat", city.get("lat").asDouble());
                cityData.put("lon", city.get("lon").asDouble());

                if (city.has("state")) {
                    cityData.put("state", city.get("state").asText());
                }

                cities.add(cityData);
            }

            return cities;

        } catch (Exception e) {
            logger.error("Error searching cities with query {}: {}", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get current weather by coordinates (lat/lon)
     */
    public Map<String, Object> getCurrentWeatherByCoordinates(double lat, double lon) {
        try {
            String url = String.format("%s/weather?lat=%f&lon=%f&appid=%s&units=metric",
                    baseUrl, lat, lon, apiKey);

            logger.info("Fetching current weather for coordinates: {}, {}", lat, lon);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode jsonNode = objectMapper.readTree(response);
            return objectMapper.convertValue(jsonNode, Map.class);

        } catch (HttpClientErrorException e) {
            logger.error("HTTP error getting weather for coordinates {}, {}: {}", lat, lon, e.getMessage());
            throw new RuntimeException("Location not found for coordinates: " + lat + ", " + lon);
        } catch (Exception e) {
            logger.error("Error getting weather for coordinates {}, {}: {}", lat, lon, e.getMessage());
            throw new RuntimeException("Error fetching weather data: " + e.getMessage());
        }
    }
}