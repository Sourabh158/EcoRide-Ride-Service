package com.ecoride.ride_service.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String mobileNumber; // User table ke column se match hona chahiye
    private String role;
    private Boolean isApproved;
    public void setApproved(boolean approved) {
        this.isApproved = approved;
    }

    public boolean isApproved() {
        return isApproved;
    }
}