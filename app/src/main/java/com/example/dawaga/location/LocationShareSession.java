package com.example.dawaga.location;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.dawaga.LocationStompManager;

/**
 * 약속 하나에 대한 내 위치 공유.
 * 위치를 받을 때마다 남은 거리를 계산해 STOMP로 보내고, 50m 안에 들어오면
 * arrived=true를 한 번 보낸 뒤 수집을 멈춘다.
 *
 * STOMP 연결(connect, disconnect)은 화면이 관리한다. 내가 도착한 뒤에도
 * 다른 사람 위치는 계속 받아야 하기 때문이다. 이 클래스는 보내기만 한다.
 *
 * 사용 예 (도착 현황 화면):
 * <pre>
 * // onCreate
 * stompManager = new LocationStompManager();
 * session = new LocationShareSession(this, stompManager,
 *         myUserId, myNickname, destLat, destLng, listener);
 *
 * // onResume
 * stompManager.connect(roomCode, location -> { 하단 리스트 갱신 });
 * if (LocationRules.isShareWindow(System.currentTimeMillis(), meetTime)) session.start();
 *
 * // onPause
 * session.stop();
 * stompManager.disconnect();
 * </pre>
 */
public class LocationShareSession {

    public interface Listener {
        /** 내 남은 거리와 추정 시간이 바뀔 때 (내 카드 갱신용) */
        void onMyProgress(double distanceM, int etaMinutes);

        /** 도착 판정 직후 1번 */
        void onArrived();

        /** 위치 권한이 없어 시작하지 못했을 때 */
        void onPermissionMissing();
    }

    private final LocationTracker tracker;
    private final LocationStompManager stompManager;
    private final String userId;
    private final String nickname;
    private final double destLat;
    private final double destLng;
    private final Listener listener;
    private boolean arrived;

    public LocationShareSession(@NonNull Context context,
                                @NonNull LocationStompManager stompManager,
                                @NonNull String userId,
                                @NonNull String nickname,
                                double destLat,
                                double destLng,
                                @NonNull Listener listener) {
        this(new LocationTracker(context), stompManager, userId, nickname, destLat, destLng, listener);
    }

    /** 테스트나 수집 주기를 바꿀 때 트래커를 직접 넘긴다 */
    public LocationShareSession(@NonNull LocationTracker tracker,
                                @NonNull LocationStompManager stompManager,
                                @NonNull String userId,
                                @NonNull String nickname,
                                double destLat,
                                double destLng,
                                @NonNull Listener listener) {
        this.tracker = tracker;
        this.stompManager = stompManager;
        this.userId = userId;
        this.nickname = nickname;
        this.destLat = destLat;
        this.destLng = destLng;
        this.listener = listener;
    }

    /** 공유 시작. 이미 도착했거나 돌고 있으면 아무것도 하지 않는다 */
    public void start() {
        if (arrived) {
            return;
        }
        boolean started = tracker.start(this::onLocation);
        if (!started) {
            listener.onPermissionMissing();
        }
    }

    public void stop() {
        tracker.stop();
    }

    public boolean isArrived() {
        return arrived;
    }

    private void onLocation(double lat, double lng, float accuracyM) {
        if (arrived || !LocationRules.isUsableFix(accuracyM)) {
            return;
        }

        double distanceM = LocationRules.distanceMeters(lat, lng, destLat, destLng);
        boolean nowArrived = LocationRules.isArrived(distanceM);

        stompManager.sendMyLocation(new MemberLocation(
                userId, nickname, lat, lng, nowArrived, System.currentTimeMillis()));

        if (nowArrived) {
            // 연결이 끊긴 순간이어도 LocationStompManager가 들고 있다가 재연결 때 보낸다
            arrived = true;
            tracker.stop();
            listener.onMyProgress(distanceM, 0);
            listener.onArrived();
            return;
        }

        listener.onMyProgress(distanceM, LocationRules.estimateMinutes(distanceM));
    }
}
