package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.ClubResponse;
import com.theclub.clubservice.service.DiscoveryService;
import com.theclub.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Club discovery for the home and clubs pages (user journey step 3). */
@RestController
@RequestMapping("/api/v1/clubs")
public class DiscoveryController {

    private final DiscoveryService discovery;

    public DiscoveryController(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    /** Clubs within {@code radiusKm} (default 25, at most 200) of the point, closest first, with their distance. */
    @GetMapping("/nearby")
    public List<ClubResponse> nearby(@RequestParam double lat, @RequestParam double lng,
                                     @RequestParam(required = false) Double radiusKm,
                                     @RequestParam(required = false) Integer limit,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return discovery.nearby(lat, lng, radiusKm, limit, caller.id());
    }

    /** Most subscribers plus followers first. */
    @GetMapping("/popular")
    public List<ClubResponse> popular(@RequestParam(required = false) Integer limit,
                                      @AuthenticationPrincipal AuthenticatedUser caller) {
        return discovery.popular(limit, caller.id());
    }
}
