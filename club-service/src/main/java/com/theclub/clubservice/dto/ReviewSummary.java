package com.theclub.clubservice.dto;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @param average  mean rating rounded to one decimal, null without reviews
 * @param byRating number of reviews per star rating, 1–5 (zero counts included)
 */
public record ReviewSummary(Double average, long count, Map<Integer, Long> byRating) {

    /** From rows of (rating, count). */
    public static ReviewSummary of(List<Object[]> ratingCounts) {
        Map<Integer, Long> byRating = new TreeMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            byRating.put(stars, 0L);
        }
        long count = 0;
        long total = 0;
        for (Object[] row : ratingCounts) {
            int stars = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            byRating.merge(stars, n, Long::sum);
            count += n;
            total += stars * n;
        }
        Double average = count == 0 ? null : Math.round(total * 10.0 / count) / 10.0;
        return new ReviewSummary(average, count, byRating);
    }
}
