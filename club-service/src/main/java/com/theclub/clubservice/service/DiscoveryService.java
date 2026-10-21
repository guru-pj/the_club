package com.theclub.clubservice.service;

import com.theclub.clubservice.dto.ClubResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.repository.ClubRepository;
import com.theclub.common.web.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Club discovery: clubs near a point and the most followed and subscribed clubs (user journey step 3). */
@Service
public class DiscoveryService {

    public static final double DEFAULT_RADIUS_KM = 25;
    public static final double MAX_RADIUS_KM = 200;
    public static final int MAX_LIMIT = 50;

    private final ClubRepository clubs;
    private final ClubService clubService;

    public DiscoveryService(ClubRepository clubs, ClubService clubService) {
        this.clubs = clubs;
        this.clubService = clubService;
    }

    /** A club with its distance from the search point. */
    public record NearbyClub(Club club, double distanceKm) {
    }

    /** Clubs with a map position within {@code radiusKm} of the point, closest first. */
    @Transactional(readOnly = true)
    public List<NearbyClub> nearbyClubs(double latitude, double longitude, Double radiusKm, Integer limit) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw ApiException.badRequest("Latitude must be within ±90 and longitude within ±180");
        }
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : Math.clamp(radiusKm, 1, MAX_RADIUS_KM);
        int max = limit == null ? 20 : Math.clamp(limit, 1, MAX_LIMIT);
        double dLat = Geo.latitudeDelta(radius);
        double dLng = Geo.longitudeDelta(radius, latitude);
        return clubs.findInBox(latitude - dLat, latitude + dLat, longitude - dLng, longitude + dLng).stream()
                .map(c -> new NearbyClub(c, Geo.distanceKm(latitude, longitude, c.getLatitude(), c.getLongitude())))
                .filter(n -> n.distanceKm() <= radius)
                .sorted(Comparator.comparingDouble(NearbyClub::distanceKm))
                .limit(max)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> nearby(double latitude, double longitude, Double radiusKm, Integer limit, UUID callerId) {
        return nearbyClubs(latitude, longitude, radiusKm, limit).stream()
                .map(n -> clubService.describe(n.club(), callerId, Math.round(n.distanceKm() * 10) / 10.0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> popular(Integer limit, UUID callerId) {
        int max = limit == null ? 10 : Math.clamp(limit, 1, MAX_LIMIT);
        return clubs.findPopular(PageRequest.of(0, max)).stream().map(c -> clubService.describe(c, callerId)).toList();
    }
}
