package com.ecoride.ride_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 1. "name" mein underscore (_) mat use karo, sirf hyphen (-) use karo
// 2. "url" ko ekdum clean rakho
@FeignClient(name = "notification-service", url = "https://ecoride-deploy-notification.onrender.com")
public interface NotificationClient {

    @PostMapping("/notifications/send-update")
    void sendUpdate(@RequestParam("email") String email, @RequestParam("message") String message);
}