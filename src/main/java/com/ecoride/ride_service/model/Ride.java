package com.ecoride.ride_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Data
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long riderId;
    private Long driverId;

    @NotBlank(message = "Source name is required")
    private String source;

    @NotBlank(message = "Destination name is required")
    private String destination;

    // --- Naye Geolocation Fields ---
    @NotNull(message = "Source Latitude is required")
    private Double sourceLat;

    @NotNull(message = "Source Longitude is required")
    private Double sourceLng;

    @NotNull(message = "Destination Latitude is required")
    private Double destinationLat;

    @NotNull(message = "Destination Longitude is required")
    private Double destinationLng;

    private Double distanceInKm; // OSRM se calculate hoga
    // -------------------------------

    private String status; // REQUESTED, ACCEPTED, COMPLETED, CANCELLED
    private Double fare;
    private String riderEmail;

    // Ride.java mein ye fields add karo
    private String razorpayOrderId;
    private String paymentId;
    private String paymentStatus;

}