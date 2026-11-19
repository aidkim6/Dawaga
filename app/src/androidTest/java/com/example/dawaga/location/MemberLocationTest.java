package com.example.dawaga.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** STOMP 메시지 JSON 변환 확인 (org.json을 쓰므로 기기 또는 에뮬레이터에서 실행) */
@RunWith(AndroidJUnit4.class)
public class MemberLocationTest {

    @Test
    public void 보낸_메시지를_그대로_다시_읽을_수_있다() {
        MemberLocation sent = new MemberLocation("user_ab12cd34", "주하",
                37.5926, 127.0164, true, 1_800_000_000_000L);

        MemberLocation read = MemberLocation.fromJson(sent.toJson());

        assertNotNull(read);
        assertEquals("user_ab12cd34", read.userId);
        assertEquals("주하", read.nickname);
        assertEquals(37.5926, read.latitude, 1e-9);
        assertEquals(127.0164, read.longitude, 1e-9);
        assertTrue(read.arrived);
        assertEquals(1_800_000_000_000L, read.timestamp);
    }

    @Test
    public void 선택_필드가_없으면_기본값() {
        MemberLocation read = MemberLocation.fromJson(
                "{\"userId\":\"user_1\",\"latitude\":37.1,\"longitude\":127.2}");

        assertNotNull(read);
        assertEquals("user_1", read.nickname);
        assertFalse(read.arrived);
        assertEquals(0L, read.timestamp);
    }

    @Test
    public void 필수_필드가_없거나_JSON이_아니면_null() {
        assertNull(MemberLocation.fromJson("{\"userId\":\"user_1\",\"latitude\":37.1}"));
        assertNull(MemberLocation.fromJson("not json"));
        assertNull(MemberLocation.fromJson(null));
    }
}
