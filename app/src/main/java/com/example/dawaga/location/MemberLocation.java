package com.example.dawaga.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * STOMP로 주고받는 멤버 위치 메시지 한 건.
 *
 * JSON 필드: userId, nickname, latitude, longitude, arrived, timestamp
 * (12주차 진행상황 "STOMP 연결 규칙 확정" 항목 기준. 서버와 필드 이름을 맞춰야 한다)
 */
public final class MemberLocation {

    public final String userId;
    public final String nickname;
    public final double latitude;
    public final double longitude;
    public final boolean arrived;
    /** 보낸 기기 시각(ms). 도착 순위는 서버가 받은 시각으로 매기는 것을 전제로 한다 */
    public final long timestamp;

    public MemberLocation(@NonNull String userId, @NonNull String nickname,
                          double latitude, double longitude,
                          boolean arrived, long timestamp) {
        this.userId = userId;
        this.nickname = nickname;
        this.latitude = latitude;
        this.longitude = longitude;
        this.arrived = arrived;
        this.timestamp = timestamp;
    }

    @NonNull
    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("nickname", nickname);
            json.put("latitude", latitude);
            json.put("longitude", longitude);
            json.put("arrived", arrived);
            json.put("timestamp", timestamp);
            return json.toString();
        } catch (JSONException e) {
            // 위도·경도가 NaN이나 무한대일 때만 발생
            throw new IllegalArgumentException("좌표 값이 잘못되었습니다", e);
        }
    }

    /** 형식이 맞지 않으면 null. userId·latitude·longitude는 필수 */
    @Nullable
    public static MemberLocation fromJson(@Nullable String payload) {
        if (payload == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(payload);
            String userId = json.getString("userId");
            return new MemberLocation(
                    userId,
                    json.optString("nickname", userId),
                    json.getDouble("latitude"),
                    json.getDouble("longitude"),
                    json.optBoolean("arrived", false),
                    json.optLong("timestamp", 0L));
        } catch (JSONException e) {
            return null;
        }
    }
}
