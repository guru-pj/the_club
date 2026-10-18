package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.LeaderboardResponse;
import com.theclub.clubservice.service.LeaderboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/api/v1/clubs/{clubId}/leaderboard")
    public LeaderboardResponse leaderboard(@PathVariable UUID clubId,
                                           @RequestParam(defaultValue = "DISTANCE") LeaderboardService.Metric metric,
                                           @RequestParam(defaultValue = "MONTH") LeaderboardService.Period period) {
        return leaderboardService.leaderboard(clubId, metric, period);
    }
}
