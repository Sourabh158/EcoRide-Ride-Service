package com.ecoride.ride_service.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DistanceService {

    private final String OSRM_API_URL = "http://router.project-osrm.org/route/v1/driving/";

    public double calculateDistance(double sLat, double sLng, double dLat, double dLng) {
        try {
            // Timeout set karne ke liye SimpleClientHttpRequestFactory use karo
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000); // 3 seconds
            factory.setReadTimeout(3000);

            RestTemplate restTemplate = new RestTemplate(factory);
            String url = OSRM_API_URL + sLng + "," + sLat + ";" + dLng + "," + dLat + "?overview=false";

            // Timeout hone par ye catch block mein chala jayega
            String response = restTemplate.getForObject(url, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            if (root.has("routes") && root.path("routes").size() > 0) {
                double distanceInMeters = root.path("routes").get(0).path("distance").asDouble();
                return distanceInMeters / 1000.0;
            }
            return 15.0; // Default fallback
        } catch (Exception e) {
            System.out.println("Maps/Timeout Error: " + e.getMessage());
            return 15.0; // Agar net slow hai toh 15km maan lo
        }

    }
}