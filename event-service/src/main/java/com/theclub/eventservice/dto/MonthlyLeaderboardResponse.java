package com.theclub.eventservice.dto;

import java.util.List;
import java.util.UUID;

public record MonthlyLeaderboardResponse(String month, List<IndividualEntry> topIndividuals,
                                         List<ClubEntry> topClubs) {

    public record IndividualEntry(int rank, UUID userId, String name, long points, long eventsAttended) {
    }

    public record ClubEntry(int rank, UUID clubId, String name, long points, long participations) {
    }
}
