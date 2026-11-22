package com.example.dawaga.location;

import java.util.Locale;

/**
 * 위치 공유 시작(약속 10분 전)까지 남은 시간 계산. 안드로이드 API를 쓰지 않아 JVM 단위 테스트 가능.
 */
public final class ShareCountdown {

    private ShareCountdown() {
    }

    /** 위치 공유가 시작되는 시각(ms) = 약속 시각 - 10분 */
    public static long shareStartMillis(long meetTimeMillis) {
        return meetTimeMillis - LocationRules.SHARE_START_BEFORE_MS;
    }

    /** 공유 시작까지 남은 시간(ms). 이미 시작됐으면 0 */
    public static long remainingMillis(long nowMillis, long meetTimeMillis) {
        return Math.max(0L, shareStartMillis(meetTimeMillis) - nowMillis);
    }

    /** 남은 시간 표시. 1시간 미만은 "08:42", 이상은 "1:08:42". 초는 올림 */
    public static String format(long remainingMillis) {
        long totalSec = (remainingMillis + 999) / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.KOREA, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.KOREA, "%02d:%02d", m, s);
    }

    /**
     * 진행 막대 비율(0~1). 화면을 연 시각부터 공유 시작 시각까지를 1로 본다.
     */
    public static float progress(long nowMillis, long openedAtMillis, long meetTimeMillis) {
        long total = shareStartMillis(meetTimeMillis) - openedAtMillis;
        if (total <= 0) {
            return 1f;
        }
        float ratio = (float) (nowMillis - openedAtMillis) / total;
        return Math.max(0f, Math.min(1f, ratio));
    }
}
