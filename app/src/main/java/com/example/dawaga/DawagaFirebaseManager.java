package com.example.dawaga;

import android.util.Log;

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
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration membersListener;

    // 1. 유저 닉네임 등록
    public interface OnUserRegisteredListener {
        void onSuccess(String userId, String nickname);
        void onFailure(Exception e);
    }

    public void registerUser(String nickname, OnUserRegisteredListener listener) {
        String userId = "user_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("nickname", nickname);

        db.collection("users").document(userId).set(userData)
                .addOnSuccessListener(aVoid -> listener.onSuccess(userId, nickname))
                .addOnFailureListener(listener::onFailure);
    }

    // 2. 약속 방 생성 (중복 코드 확인 후 생성)
    public interface OnRoomCallback {
        void onSuccess(Map<String, Object> roomData);
        void onFailure(Exception e);
    }

    public void createRoom(String roomName, double destLat, double destLng, long meetTimeMillis, OnRoomCallback callback) {
        createUniqueRoom(roomName, destLat, destLng, meetTimeMillis, 0, callback);
    }

    private void createUniqueRoom(
            String roomName,
            double destLat,
            double destLng,
            long meetTimeMillis,
            int retryCount,
            OnRoomCallback callback
    ) {
        if (retryCount >= 5) {
            callback.onFailure(new Exception("방 코드 생성에 실패했습니다. 다시 시도해 주세요."));
            return;
        }

        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        db.collection("rooms").document(roomCode).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // 이미 존재하는 코드면 다시 생성
                        createUniqueRoom(roomName, destLat, destLng, meetTimeMillis, retryCount + 1, callback);
                    } else {
                        // 겹치지 않는 코드면 방 생성
                        Map<String, Object> roomData = new HashMap<>();
                        roomData.put("roomCode", roomCode);
                        roomData.put("roomName", roomName);
                        roomData.put("destLatitude", destLat);
                        roomData.put("destLongitude", destLng);
                        roomData.put("meetTime", meetTimeMillis);

                        db.collection("rooms").document(roomCode).set(roomData)
                                .addOnSuccessListener(aVoid -> callback.onSuccess(roomData))
                                .addOnFailureListener(callback::onFailure);
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    // 3. 방 코드 조회
    public void joinRoom(String roomCode, OnRoomCallback callback) {
        db.collection("rooms").document(roomCode.toUpperCase()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.getData() != null) {
                        callback.onSuccess(documentSnapshot.getData());
                    } else {
                        callback.onFailure(new Exception("존재하지 않는 방 코드입니다."));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    // 4. 내 위치 전송
    public void updateMyLocation(
            String roomCode,
            String userId,
            String nickname,
            double lat,
            double lng,
            boolean isArrived
    ) {

        com.google.firebase.firestore.DocumentReference memberRef =
                db.collection("rooms")
                        .document(roomCode.toUpperCase())
                        .collection("members")
                        .document(userId);

        db.runTransaction(transaction -> {

            DocumentSnapshot snapshot =
                    transaction.get(memberRef);

            // 이미 도착한 사람은 상태와 도착 시간을 유지
            if (snapshot.exists()
                    && "ARRIVED".equals(snapshot.getString("status"))) {
                return null;
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

            return null;

        }).addOnSuccessListener(aVoid ->
                Log.d(TAG, "위치 저장 성공")
        ).addOnFailureListener(e ->
                Log.e(TAG, "위치 저장 실패", e)
        );
    }


    // 5. 방 멤버 실시간 수신
    public interface OnMembersUpdatedListener {
        void onMembersUpdated(List members);
    }

    public void listenRoomMembers(String roomCode, OnMembersUpdatedListener listener) {
        stopListening();
        membersListener = db.collection("rooms").document(roomCode.toUpperCase())
                .collection("members")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        Log.e(TAG, "실시간 수신 에러", e);
                        return;
                    }
                    List memberList = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        if (doc.getData() != null) {
                            memberList.add(doc.getData());
                        }
                    }
                    listener.onMembersUpdated(memberList);
                });
    }

    public void stopListening() {
        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
    }
}