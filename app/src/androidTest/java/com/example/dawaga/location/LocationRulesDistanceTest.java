package com.example.dawaga.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Location.distanceBetween을 쓰는 거리 계산 확인 (기기 또는 에뮬레이터에서 실행) */
@RunWith(AndroidJUnit4.class)
public class LocationRulesDistanceTest {

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
        assertEquals(111.0, d, 1.0);
    }

    @Test
    public void 위도_경도가_함께_바뀌면_평면_근사값과_비슷() {
        // 위도 0.0116도(약 1290m), 경도 0.0072도(위도 37.56도에서 약 635m) -> 평면 근사 약 1438m
        double d = LocationRules.distanceMeters(37.5547, 126.9707, 37.5663, 126.9779);
        assertEquals(1438, d, 15);
    }

    @Test
    public void 약속_장소_40m_안쪽은_도착_60m_바깥은_미도착() {
        // 위도 1도 약 111km -> 0.00036도 약 40m, 0.00054도 약 60m
        double near = LocationRules.distanceMeters(DEST_LAT + 0.00036, DEST_LNG, DEST_LAT, DEST_LNG);
        double far = LocationRules.distanceMeters(DEST_LAT + 0.00054, DEST_LNG, DEST_LAT, DEST_LNG);
        assertTrue(LocationRules.isArrived(near));
        assertFalse(LocationRules.isArrived(far));
    }
}
