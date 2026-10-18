package com.theclub.eventservice.controller;

import com.theclub.eventservice.dto.MonthlyLeaderboardResponse;
import com.theclub.eventservice.service.ParticipationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboards")
public class LeaderboardController {

    private final ParticipationService participationService;

    public LeaderboardController(ParticipationService participationService) {
        this.participationService = participationService;
    }

    /**
     * @param month YYYY-MM, defaults to the current month.
     */
    @GetMapping("/monthly")
    public MonthlyLeaderboardResponse monthly(@RequestParam(required = false) String month,
                                              @RequestParam(defaultValue = "10") int limit) {
        return participationService.monthly(month, limit);
    }
}
