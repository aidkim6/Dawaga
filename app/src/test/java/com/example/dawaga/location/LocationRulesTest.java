package com.example.dawaga.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 안드로이드 API를 쓰지 않는 규칙만 확인한다.
 * 거리 계산(distanceMeters)은 androidTest의 LocationRulesDistanceTest에서 확인한다.
 */
public class LocationRulesTest {

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
