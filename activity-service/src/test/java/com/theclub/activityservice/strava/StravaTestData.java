package com.theclub.activityservice.strava;

/**
 * Builders for Strava API payloads used across tests.
 */
final class StravaTestData {

    private StravaTestData() {
    }

    static StravaActivity activity(long id, String name, long ownerAthlete) {
        return activity(id, name, ownerAthlete, false);
    }

    static StravaActivity activity(long id, String name, long ownerAthlete, boolean privateActivity) {
        return new StravaActivity(id, name, "Run", "TrailRun", 10500.0, 3600, 3700, 812.4,
                "2026-10-18T00:30:00Z", "2026-10-18T06:00:00Z", "(GMT+05:30) Asia/Kolkata", 120.5, 152.3,
                "Garmin Forerunner 265", privateActivity, privateActivity ? "only_me" : "everyone",
                new StravaTokenResponse.Athlete(ownerAthlete));
    }

    /** JSON as returned by GET /athlete/activities for one activity. */
    static String activityJson(long id, long ownerAthlete, String startDateLocal) {
        return """
                {"id":%d,"name":"Run %d","type":"Run","sport_type":"Run","distance":5000.0,"moving_time":1500,
                 "elapsed_time":1600,"start_date":"2026-10-01T00:30:00Z","start_date_local":"%s",
                 "timezone":"(GMT+05:30) Asia/Kolkata","private":false,"athlete":{"id":%d}}"""
                .formatted(id, id, startDateLocal, ownerAthlete);
    }
}
