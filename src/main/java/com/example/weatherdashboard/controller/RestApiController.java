package com.example.weatherdashboard.controller;


import com.example.weatherdashboard.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class RestApiController {

    @Autowired
    private WeatherService weatherService;

    @GetMapping("/current/{city}")
    public ResponseEntity<Map<String, Object>> getCurrentWeather(@PathVariable String city) {
        try {
            Map<String, Object> weather = weatherService.getCurrentWeather(city);
            return ResponseEntity.ok(weather);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchCities(@RequestParam String query) {
        try {
            List<Map<String, Object>> cities = weatherService.searchCities(query);
            return ResponseEntity.ok(cities);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @GetMapping("/current/coordinates")
    public ResponseEntity<Map<String, Object>> getCurrentWeatherByCoordinates(@RequestParam double lat, @RequestParam double lon) {
        try {
            Map<String, Object> weather = weatherService.getCurrentWeatherByCoordinates(lat, lon);
            return ResponseEntity.ok(weather);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}