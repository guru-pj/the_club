package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.ActivityType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StravaTypeMapperTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "TrailRun, Run, RUN",
            "VirtualRun, null, RUN",
            "GravelRide, Ride, RIDE",
            "EBikeRide, null, RIDE",
            "Walk, Walk, WALK",
            "Hike, null, HIKE",
            "Swim, null, SWIM",
            "Yoga, null, YOGA",
            "WeightTraining, null, WORKOUT",
            "Crossfit, null, WORKOUT",
            "Pickleball, null, OTHER",
            "null, Ride, RIDE",
            "Unknown, Run, RUN",
            "null, null, OTHER"
    })
    void mapsSportTypeThenLegacyType(String sportType, String type, ActivityType expected) {
        assertThat(StravaTypeMapper.map(sportType, type)).isEqualTo(expected);
    }
}
