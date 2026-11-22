package com.example.dawaga.location;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShareCountdownTest {

    private static final long MEET = 1_800_000_000_000L;
    private static final long SHARE_START = MEET - 10 * 60 * 1000L;

    @Test
    public void 공유_시작은_약속_10분_전() {
        assertEquals(SHARE_START, ShareCountdown.shareStartMillis(MEET));
    }

    @Test
    public void 남은_시간은_공유_시작까지이고_지나면_0() {
        assertEquals(90_000L, ShareCountdown.remainingMillis(SHARE_START - 90_000L, MEET));
        assertEquals(0L, ShareCountdown.remainingMillis(SHARE_START, MEET));
        assertEquals(0L, ShareCountdown.remainingMillis(SHARE_START + 5_000L, MEET));
    }

    @Test
    public void 한시간_미만은_분초_이상은_시분초_표시() {
        assertEquals("08:42", ShareCountdown.format((8 * 60 + 42) * 1000L));
        assertEquals("00:00", ShareCountdown.format(0));
        assertEquals("1:08:42", ShareCountdown.format((3600 + 8 * 60 + 42) * 1000L));
    }

    @Test
    public void 초는_올림해서_0초가_되기_전까지_00_00으로_보이지_않음() {
        assertEquals("00:01", ShareCountdown.format(1));
        assertEquals("00:01", ShareCountdown.format(999));
        assertEquals("01:00", ShareCountdown.format(59_001));
    }

    @Test
    public void 진행_비율은_화면을_연_시각부터_공유_시작까지() {
        long opened = SHARE_START - 100_000L;
        assertEquals(0f, ShareCountdown.progress(opened, opened, MEET), 1e-6);
        assertEquals(0.5f, ShareCountdown.progress(opened + 50_000L, opened, MEET), 1e-6);
        assertEquals(1f, ShareCountdown.progress(SHARE_START + 1, opened, MEET), 1e-6);
        // 이미 공유 시간이 지난 뒤 화면을 열면 가득 찬 상태
        assertEquals(1f, ShareCountdown.progress(SHARE_START + 10, SHARE_START + 5, MEET), 1e-6);
    }
}
