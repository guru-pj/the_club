package com.theclub.clubservice.service;

/** Great-circle distances for "nearby" searches. */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Degrees of latitude spanning {@code km}. */
    public static double latitudeDelta(double km) {
        return Math.toDegrees(km / EARTH_RADIUS_KM);
    }

    /** Degrees of longitude spanning {@code km} at {@code latitude}; the whole circle near the poles. */
    public static double longitudeDelta(double km, double latitude) {
        double cos = Math.cos(Math.toRadians(latitude));
        return cos < 1e-6 ? 180 : Math.min(180, Math.toDegrees(km / (EARTH_RADIUS_KM * cos)));
    }
}
