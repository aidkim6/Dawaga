package com.example.dawaga.location;

import java.util.Locale;

/**
 * 위치 공유 규칙과 계산 모음 (안드로이드 API를 쓰지 않아 JVM 단위 테스트 가능).
 *
 * - 공유 시작: 약속 10분 전부터 (기획서 v2 기능 6)
 * - 도착 판정: 약속 장소 반경 50m 이내 (기획서 v2 기능 9)
 * - 추정 시간: 도보 시속 4km(분당 약 67m) 기준 직선거리로 계산 (팀 확정 전 임시값)
 */
public final class LocationRules {

    /** 약속 몇 분 전부터 위치를 공유할지 */
    public static final long SHARE_START_BEFORE_MS = 10 * 60 * 1000L;

    /** 이 거리(m) 안으로 들어오면 도착 */
    public static final double ARRIVAL_RADIUS_M = 50.0;

    /** 추정 시간 계산용 도보 속도(m/분). 시속 4km */
    public static final double WALKING_METERS_PER_MIN = 4000.0 / 60.0;

    /** 오차가 이보다 큰 좌표는 버린다(m). 대략적 위치만 허용된 경우 등 */
    public static final float MAX_ACCURACY_M = 100f;

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private LocationRules() {
    }

    /** 두 좌표 사이 직선거리(m). 하버사인 공식 */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /** 남은 거리로 추정 소요 시간(분, 올림). 도착 반경 안이면 0 */
    public static int estimateMinutes(double distanceM) {
        if (distanceM <= ARRIVAL_RADIUS_M) {
            return 0;
        }
        return (int) Math.ceil(distanceM / WALKING_METERS_PER_MIN);
    }

    public static boolean isArrived(double distanceM) {
        return distanceM <= ARRIVAL_RADIUS_M;
    }

    /** 지금이 위치 공유 구간인지 (약속 10분 전부터) */
    public static boolean isShareWindow(long nowMillis, long meetTimeMillis) {
        return nowMillis >= meetTimeMillis - SHARE_START_BEFORE_MS;
    }

    /** 좌표 오차가 판정에 쓸 만한지. 오차 정보가 없으면(0 이하) 그대로 사용 */
    public static boolean isUsableFix(float accuracyM) {
        return accuracyM <= 0f || accuracyM <= MAX_ACCURACY_M;
    }

    /** 화면 표시용: 1km 미만은 "350m", 이상은 "1.2km" */
    public static String formatDistance(double distanceM) {
        if (distanceM < 1000) {
            return Math.round(distanceM) + "m";
        }
        return String.format(Locale.KOREA, "%.1fkm", distanceM / 1000.0);
    }
}
