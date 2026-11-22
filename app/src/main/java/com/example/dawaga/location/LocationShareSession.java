package com.example.dawaga.location;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.dawaga.DawagaFirebaseManager;

/**
 * 약속 하나에 대한 내 위치 공유.
 * 위치를 받을 때마다 남은 거리를 계산해 Firestore(rooms/{방코드}/members/{userId})에 올리고,
 * 50m 안에 들어오면 도착(status=ARRIVED)으로 올린 뒤 수집을 멈춘다.
 *
 * 다른 사람 위치 수신과 목록 표시는 ArrivalStatusActivity(listenRoomMembers)와
 * MemberStatusAdapter가 맡는다. 이 클래스는 내 위치를 올리기만 한다.
 *
 * 사용 예 (ArrivalStatusActivity):
 * <pre>
 * // onCreate
 * permissionHelper = new LocationPermissionHelper(this);
 * shareSession = new LocationShareSession(this, firebaseManager,
 *         roomCode, myUserId, myNickname, destinationLat, destinationLng, listener);
 *
 * // onResume
 * if (LocationRules.isShareWindow(System.currentTimeMillis(), meetTime)) {
 *     permissionHelper.request((granted, precise) -> { if (granted) shareSession.start(); });
 * }
 *
 * // onPause
 * shareSession.stop();
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
    private final DawagaFirebaseManager firebaseManager;
    private final String roomCode;
    private final String userId;
    private final String nickname;
    private final double destLat;
    private final double destLng;
    private final Listener listener;
    private boolean arrived;

    public LocationShareSession(@NonNull Context context,
                                @NonNull DawagaFirebaseManager firebaseManager,
                                @NonNull String roomCode,
                                @NonNull String userId,
                                @NonNull String nickname,
                                double destLat,
                                double destLng,
                                @NonNull Listener listener) {
        this(new LocationTracker(context), firebaseManager, roomCode, userId, nickname,
                destLat, destLng, listener);
    }

    /** 테스트나 수집 주기를 바꿀 때 트래커를 직접 넘긴다 */
    public LocationShareSession(@NonNull LocationTracker tracker,
                                @NonNull DawagaFirebaseManager firebaseManager,
                                @NonNull String roomCode,
                                @NonNull String userId,
                                @NonNull String nickname,
                                double destLat,
                                double destLng,
                                @NonNull Listener listener) {
        this.tracker = tracker;
        this.firebaseManager = firebaseManager;
        this.roomCode = roomCode;
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

        // 네트워크가 끊겨 있어도 Firestore SDK가 쓰기를 기기에 보관했다가 연결되면 올린다
        firebaseManager.updateMyLocation(roomCode, userId, nickname, lat, lng, nowArrived);

        if (nowArrived) {
            arrived = true;
            tracker.stop();
            listener.onMyProgress(distanceM, 0);
            listener.onArrived();
            return;
        }

        listener.onMyProgress(distanceM, LocationRules.estimateMinutes(distanceM));
    }
}
