package com.example.dawaga.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationRulesTest {

    // 성신여대입구역 (MainActivity 예시 좌표)
    private static final double DEST_LAT = 37.5926;
    private static final double DEST_LNG = 127.0164;

    @Test
    public void 같은_좌표는_거리_0() {
        assertEquals(0.0, LocationRules.distanceMeters(DEST_LAT, DEST_LNG, DEST_LAT, DEST_LNG), 1e-6);
    }

    @Test
    public void 위도_0_001도는_약_111m() {
        double d = LocationRules.distanceMeters(DEST_LAT, DEST_LNG, DEST_LAT + 0.001, DEST_LNG);
        assertEquals(111.2, d, 1.0);
    }

    @Test
    public void 위도_경도가_함께_바뀌면_평면_근사값과_비슷() {
        // 위도 0.0116도(약 1290m), 경도 0.0072도(위도 37.56도에서 약 635m) -> 평면 근사 약 1438m
        double d = LocationRules.distanceMeters(37.5547, 126.9707, 37.5663, 126.9779);
        assertEquals(1438, d, 10);
    }

    @Test
    public void 반경_50m_경계에서_도착_판정() {
        assertTrue(LocationRules.isArrived(50.0));
        assertTrue(LocationRules.isArrived(12.3));
        assertFalse(LocationRules.isArrived(50.1));
    }

    @Test
    public void 추정시간은_분당_약_67m_올림() {
        assertEquals(0, LocationRules.estimateMinutes(30));
        assertEquals(1, LocationRules.estimateMinutes(60));
        assertEquals(2, LocationRules.estimateMinutes(70));
        assertEquals(15, LocationRules.estimateMinutes(1000));
    }

    @Test
    public void 공유는_약속_10분_전부터() {
        long meet = 1_800_000_000_000L;
        assertFalse(LocationRules.isShareWindow(meet - 10 * 60 * 1000L - 1, meet));
        assertTrue(LocationRules.isShareWindow(meet - 10 * 60 * 1000L, meet));
        assertTrue(LocationRules.isShareWindow(meet + 5 * 60 * 1000L, meet));
    }

    @Test
    public void 오차_100m_넘는_좌표는_버림() {
        assertTrue(LocationRules.isUsableFix(0f));
        assertTrue(LocationRules.isUsableFix(15f));
        assertTrue(LocationRules.isUsableFix(100f));
        assertFalse(LocationRules.isUsableFix(500f));
    }

    @Test
    public void 거리_표시_형식() {
        assertEquals("350m", LocationRules.formatDistance(349.6));
        assertEquals("999m", LocationRules.formatDistance(999.4));
        assertEquals("1.2km", LocationRules.formatDistance(1234));
    }
}
