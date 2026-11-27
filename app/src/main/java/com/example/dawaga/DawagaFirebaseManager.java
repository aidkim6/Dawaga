
package com.example.dawaga;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DawagaFirebaseManager {

    private static final String TAG = "DawagaFirebase";

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private ListenerRegistration membersListener;
    private ListenerRegistration roomListener;
    private ListenerRegistration resultsListener;

    // ==============================
    // 사용자 등록
    // ==============================

    public interface OnUserRegisteredListener {
        void onSuccess(String userId, String nickname);
        void onFailure(Exception e);
    }

    public void registerUser(
            Context context,
            String nickname,
            OnUserRegisteredListener listener
    ) {
        String userId = "user_" +
                UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("nickname", nickname);

        db.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {

                    SharedPreferences prefs =
                            context.getSharedPreferences(
                                    "DawagaPrefs",
                                    Context.MODE_PRIVATE
                            );

                    prefs.edit()
                            .putString("userId", userId)
                            .putString("nickname", nickname)
                            .apply();

                    listener.onSuccess(userId, nickname);
                })
                .addOnFailureListener(listener::onFailure);
    }

    // ==============================
    // 약속방 생성
    // ==============================

    public interface OnRoomCallback {
        void onSuccess(Map<String, Object> roomData);
        void onFailure(Exception e);
    }

    public void createRoom(
            String roomName,
            double destLat,
            double destLng,
            long meetTimeMillis,
            String hostId,
            OnRoomCallback callback
    ) {
        if (hostId == null || hostId.trim().isEmpty()) {
            callback.onFailure(
                    new Exception("방장 사용자 정보가 없습니다.")
            );
            return;
        }

        createUniqueRoom(
                roomName,
                destLat,
                destLng,
                meetTimeMillis,
                hostId,
                0,
                callback
        );
    }

    private void createUniqueRoom(
            String roomName,
            double destLat,
            double destLng,
            long meetTimeMillis,
            String hostId,
            int retryCount,
            OnRoomCallback callback
    ) {
        if (retryCount >= 5) {
            callback.onFailure(
                    new Exception("방 코드 생성에 실패했습니다.")
            );
            return;
        }

        String roomCode = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();

        db.collection("rooms")
                .document(roomCode)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.exists()) {
                        createUniqueRoom(
                                roomName,
                                destLat,
                                destLng,
                                meetTimeMillis,
                                hostId,
                                retryCount + 1,
                                callback
                        );
                        return;
                    }

                    Map<String, Object> roomData = new HashMap<>();

                    roomData.put("roomCode", roomCode);
                    roomData.put("roomName", roomName);
                    roomData.put("destLatitude", destLat);
                    roomData.put("destLongitude", destLng);
                    roomData.put("meetTime", meetTimeMillis);
                    roomData.put("hostId", hostId);
                    roomData.put("status", "ACTIVE");

                    roomData.put(
                            "expirationTime",
                            meetTimeMillis + 60 * 60 * 1000L
                    );

                    roomData.put(
                            "createdAt",
                            System.currentTimeMillis()
                    );

                    db.collection("rooms")
                            .document(roomCode)
                            .set(roomData)
                            .addOnSuccessListener(aVoid ->
                                    callback.onSuccess(roomData))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    // ==============================
    // 약속방 참여 및 정보 조회
    // ==============================

    public void joinRoom(
            String roomCode,
            OnRoomCallback callback
    ) {
        if (roomCode == null || roomCode.trim().isEmpty()) {
            callback.onFailure(
                    new Exception("방 코드가 없습니다.")
            );
            return;
        }

        db.collection("rooms")
                .document(roomCode.toUpperCase())
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!snapshot.exists()
                            || snapshot.getData() == null) {
                        callback.onFailure(
                                new Exception("존재하지 않는 방 코드입니다.")
                        );
                        return;
                    }

                    String status = snapshot.getString("status");

                    if (!"ACTIVE".equals(status)) {
                        callback.onFailure(
                                new Exception("현재 참여할 수 없는 약속방입니다.")
                        );
                        return;
                    }

                    callback.onSuccess(snapshot.getData());
                })
                .addOnFailureListener(callback::onFailure);
    }

    // ==============================
    // 내 위치 업데이트
    // ==============================

    public void updateMyLocation(
            String roomCode,
            String userId,
            String nickname,
            double lat,
            double lng,
            boolean isArrived
    ) {
        if (roomCode == null || userId == null) {
            return;
        }

        DocumentReference roomRef =
                db.collection("rooms")
                        .document(roomCode.toUpperCase());

        DocumentReference memberRef =
                roomRef.collection("members")
                        .document(userId);

        db.runTransaction(transaction -> {

            DocumentSnapshot roomSnapshot =
                    transaction.get(roomRef);

            if (!roomSnapshot.exists()
                    || !"ACTIVE".equals(
                    roomSnapshot.getString("status"))) {
                return false;
            }

            DocumentSnapshot memberSnapshot =
                    transaction.get(memberRef);

            if (memberSnapshot.exists()
                    && "ARRIVED".equals(
                    memberSnapshot.getString("status"))) {
                return false;
            }

            Map<String, Object> memberData = new HashMap<>();

            memberData.put("userId", userId);
            memberData.put("nickname", nickname);
            memberData.put("latitude", lat);
            memberData.put("longitude", lng);

            memberData.put(
                    "status",
                    isArrived ? "ARRIVED" : "MOVING"
            );

            if (isArrived) {
                memberData.put(
                        "arrivedAt",
                        System.currentTimeMillis()
                );
            }

            transaction.set(
                    memberRef,
                    memberData,
                    SetOptions.merge()
            );

            return true;

        }).addOnSuccessListener(updated -> {

            if (updated) {
                Log.d(TAG, "위치 저장 성공");
            } else {
                Log.d(TAG, "방이 종료되었거나 이미 도착한 사용자입니다.");
            }

        }).addOnFailureListener(e ->
                Log.e(TAG, "위치 저장 실패", e)
        );
    }

    // ==============================
    // 방장 확인
    // ==============================

    public interface OnRoomHostListener {
        void onSuccess(String hostId);
        void onFailure(Exception e);
    }

    public void getRoomHostId(
            String roomCode,
            OnRoomHostListener listener
    ) {
        db.collection("rooms")
                .document(roomCode.toUpperCase())
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.exists()) {

                        String hostId = snapshot.getString("hostId");

                        if (hostId != null) {
                            listener.onSuccess(hostId);
                        } else {
                            listener.onFailure(
                                    new Exception("방장 정보를 찾을 수 없습니다.")
                            );
                        }

                    } else {
                        listener.onFailure(
                                new Exception("존재하지 않는 방입니다.")
                        );
                    }
                })
                .addOnFailureListener(listener::onFailure);
    }

    // ==============================
    // 방 상태 실시간 감지
    // ==============================

    public interface OnRoomStatusListener {
        void onStatusChanged(String status);
    }

    public void listenRoomStatus(
            String roomCode,
            OnRoomStatusListener listener
    ) {
        if (roomListener != null) {
            roomListener.remove();
            roomListener = null;
        }

        roomListener = db.collection("rooms")
                .document(roomCode.toUpperCase())
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        Log.e(TAG, "방 상태 감지 실패", error);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {

                        String status = snapshot.getString("status");

                        if (status != null) {
                            listener.onStatusChanged(status);
                        }
                    }
                });
    }

    // ==============================
    // 약속방 마감
    // ==============================

    public interface OnRoomClosedListener {
        void onSuccess();
        void onFailure(Exception e);
    }

    public void closeRoom(
            String roomCode,
            String requesterId,
            OnRoomClosedListener listener
    ) {
        if (roomCode == null || requesterId == null) {
            listener.onFailure(
                    new Exception("방 정보 또는 사용자 정보가 없습니다.")
            );
            return;
        }

        DocumentReference roomRef =
                db.collection("rooms")
                        .document(roomCode.toUpperCase());

        // 1단계: 방장 확인 및 CLOSING 변경
        db.runTransaction(transaction -> {

            DocumentSnapshot roomSnapshot =
                    transaction.get(roomRef);

            if (!roomSnapshot.exists()) {
                throw new IllegalStateException(
                        "존재하지 않는 약속방입니다."
                );
            }

            String hostId = roomSnapshot.getString("hostId");
            String status = roomSnapshot.getString("status");

            if (!requesterId.equals(hostId)) {
                throw new IllegalStateException(
                        "방장만 약속을 종료할 수 있습니다."
                );
            }

            if (!"ACTIVE".equals(status)) {
                throw new IllegalStateException(
                        "이미 종료되었거나 마감 처리 중인 약속입니다."
                );
            }

            transaction.update(
                    roomRef,
                    "status", "CLOSING",
                    "closingAt", System.currentTimeMillis()
            );

            Object meetTimeValue = roomSnapshot.get("meetTime");

            return meetTimeValue instanceof Number
                    ? ((Number) meetTimeValue).longValue()
                    : 0L;

        }).addOnSuccessListener(meetTime -> {

            // 2단계: 멤버 정보 조회
            roomRef.collection("members")
                    .get()
                    .addOnSuccessListener(memberSnapshots -> {

                        List<Map<String, Object>> finalResults =
                                new ArrayList<>();

                        for (DocumentSnapshot memberSnapshot :
                                memberSnapshots.getDocuments()) {

                            Map<String, Object> member =
                                    memberSnapshot.getData();

                            if (member == null) {
                                continue;
                            }

                            Map<String, Object> result =
                                    new HashMap<>(member);

                            result.put(
                                    "userId",
                                    memberSnapshot.getId()
                            );

                            Object arrivedAtValue =
                                    member.get("arrivedAt");

                            if ("ARRIVED".equals(member.get("status"))
                                    && arrivedAtValue instanceof Number) {

                                long arrivedAt =
                                        ((Number) arrivedAtValue).longValue();

                                result.put(
                                        "resultStatus",
                                        arrivedAt <= meetTime
                                                ? "ON_TIME"
                                                : "LATE"
                                );

                            } else {
                                result.put("resultStatus", "NO_SHOW");
                            }

                            finalResults.add(result);
                        }

                        // 3단계: 결과 저장 및 CLOSED 변경
                        Map<String, Object> closeData =
                                new HashMap<>();

                        long now = System.currentTimeMillis();

                        closeData.put("status", "CLOSED");
                        closeData.put("closedBy", requesterId);
                        closeData.put("closeReason", "HOST_MANUAL");
                        closeData.put("closedAt", now);
                        closeData.put("finalResults", finalResults);
                        closeData.put("resultsSavedAt", now);

                        roomRef.update(closeData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(
                                            TAG,
                                            "수동 마감 및 최종 결과 저장 완료"
                                    );
                                    listener.onSuccess();
                                })
                                .addOnFailureListener(e ->
                                        restoreRoomAfterCloseFailure(
                                                roomRef,
                                                listener,
                                                e
                                        )
                                );

                    })
                    .addOnFailureListener(e ->
                            restoreRoomAfterCloseFailure(
                                    roomRef,
                                    listener,
                                    e
                            )
                    );

        }).addOnFailureListener(listener::onFailure);
    }

    // 마감 실패 시 ACTIVE로 복구
    private void restoreRoomAfterCloseFailure(
            DocumentReference roomRef,
            OnRoomClosedListener listener,
            Exception originalError
    ) {
        roomRef.get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.exists()
                            && "CLOSING".equals(
                            snapshot.getString("status"))) {

                        roomRef.update("status", "ACTIVE")
                                .addOnCompleteListener(task -> {

                                    if (!task.isSuccessful()) {
                                        Log.e(
                                                TAG,
                                                "방 상태 복구 실패",
                                                task.getException()
                                        );
                                    }

                                    listener.onFailure(originalError);
                                });

                    } else {
                        listener.onFailure(originalError);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "방 상태 확인 실패", e);
                    listener.onFailure(originalError);
                });
    }

    // ==============================
    // 참여자 실시간 수신
    // ==============================

    public interface OnMembersUpdatedListener {
        void onMembersUpdated(List members);
    }

    public void listenRoomMembers(
            String roomCode,
            OnMembersUpdatedListener listener
    ) {
        if (membersListener != null) {
            membersListener.remove();
        }

        membersListener =
                db.collection("rooms")
                        .document(roomCode.toUpperCase())
                        .collection("members")
                        .addSnapshotListener((snapshots, e) -> {

                            if (e != null) {
                                Log.e(TAG, "멤버 수신 오류", e);
                                return;
                            }

                            if (snapshots == null) {
                                return;
                            }

                            List memberList = new ArrayList<>();

                            for (DocumentSnapshot doc :
                                    snapshots.getDocuments()) {

                                if (doc.getData() != null) {
                                    memberList.add(doc.getData());
                                }
                            }

                            listener.onMembersUpdated(memberList);
                        });
    }

    // ==============================
    // 최종 결과 실시간 수신
    // ==============================

    public interface OnFinalResultsListener {
        void onResultsUpdated(
                List<Map<String, Object>> results
        );
    }

    public void listenFinalResults(
            String roomCode,
            OnFinalResultsListener listener
    ) {
        if (resultsListener != null) {
            resultsListener.remove();
        }

        resultsListener =
                db.collection("rooms")
                        .document(roomCode.toUpperCase())
                        .addSnapshotListener((snapshot, e) -> {

                            if (e != null) {
                                Log.e(TAG, "최종 결과 수신 오류", e);
                                return;
                            }

                            if (snapshot == null || !snapshot.exists()) {
                                return;
                            }

                            List<Map<String, Object>> results =
                                    new ArrayList<>();

                            Object value = snapshot.get("finalResults");

                            if (value instanceof List<?>) {

                                for (Object item : (List<?>) value) {

                                    if (item instanceof Map<?, ?>) {

                                        Map<String, Object> result =
                                                new HashMap<>();

                                        for (Map.Entry<?, ?> entry :
                                                ((Map<?, ?>) item).entrySet()) {

                                            if (entry.getKey() instanceof String) {
                                                result.put(
                                                        (String) entry.getKey(),
                                                        entry.getValue()
                                                );
                                            }
                                        }

                                        results.add(result);
                                    }
                                }
                            }

                            listener.onResultsUpdated(results);
                        });
    }

    // ==============================
    // 리스너 해제
    // ==============================

    public void stopListening() {

        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }

        if (roomListener != null) {
            roomListener.remove();
            roomListener = null;
        }

        if (resultsListener != null) {
            resultsListener.remove();
            resultsListener = null;
        }
    }
}
