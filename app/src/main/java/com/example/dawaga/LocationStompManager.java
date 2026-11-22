package com.example.dawaga;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import io.reactivex.disposables.Disposable;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;

public class LocationStompManager {

    private static final String TAG = "STOMP";
    // 안드로이드 에뮬레이터에서 PC로 연결하는 기본 IP
    private final String serverUrl;
    private StompClient stompClient;
    private Disposable lifecycleDisposable;
    private Disposable topicDisposable;

    public LocationStompManager() {
        this("ws://10.0.2.2:8080/ws-location");
    }

    public LocationStompManager(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    // 1. 웹소켓 / STOMP 연결
    public void connect(Runnable onConnected) {
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, serverUrl);

        lifecycleDisposable = stompClient.lifecycle().subscribe(lifecycleEvent -> {
            if (lifecycleEvent.getType() == LifecycleEvent.Type.OPENED) {
                Log.d(TAG, "웹소켓 연결 성공!");
                if (onConnected != null) {
                    onConnected.run();
                }
            } else if (lifecycleEvent.getType() == LifecycleEvent.Type.ERROR) {
                Log.e(TAG, "웹소켓 에러 발생", lifecycleEvent.getException());
            } else if (lifecycleEvent.getType() == LifecycleEvent.Type.CLOSED) {
                Log.d(TAG, "웹소켓 연결 종료");
            }
        });

        stompClient.connect();
    }

    // 콜백 인터페이스 (위치 데이터 수신용)
    public interface OnLocationReceivedListener {
        void onLocationReceived(String userId, double latitude, double longitude);
    }

    // 2. 다른 사용자의 위치 수신 (구독)
    public void subscribeGroupLocation(String groupId, OnLocationReceivedListener listener) {
        if (stompClient == null) return;

        topicDisposable = stompClient.topic("/topic/group/" + groupId).subscribe(topicMessage -> {
            try {
                JSONObject json = new JSONObject(topicMessage.getPayload());
                String userId = json.getString("userId");
                double latitude = json.getDouble("latitude");
                double longitude = json.getDouble("longitude");

                Log.d(TAG, "위치 수신 성공 -> 유저: " + userId + ", Lat: " + latitude + ", Lng: " + longitude);

                if (listener != null) {
                    listener.onLocationReceived(userId, latitude, longitude);
                }
            } catch (JSONException e) {
                Log.e(TAG, "위치 파싱 에러", e);
            }
        });
    }

    // 3. 내 위치 서버로 전송 (발행)
    public void sendMyLocation(String userId, String groupId, double lat, double lng) {
        if (stompClient == null || !stompClient.isConnected()) return;

        try {
            JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("groupId", groupId);
            json.put("latitude", lat);
            json.put("longitude", lng);

            stompClient.send("/app/location.share/" + groupId, json.toString()).subscribe(() -> {
                Log.d(TAG, "내 위치 전송 완료 -> " + lat + ", " + lng);
            }, throwable -> {
                Log.e(TAG, "내 위치 전송 실패", throwable);
            });
        } catch (JSONException e) {
            Log.e(TAG, "JSON 생성 에러", e);
        }
    }

    // 4. 연결 해제
    public void disconnect() {
        if (lifecycleDisposable != null && !lifecycleDisposable.isDisposed()) {
            lifecycleDisposable.dispose();
        }
        if (topicDisposable != null && !topicDisposable.isDisposed()) {
            topicDisposable.dispose();
        }
        if (stompClient != null) {
            stompClient.disconnect();
        }
    }
}