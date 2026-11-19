package com.example.dawaga;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dawaga.location.MemberLocation;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;

/**
 * 약속 방 하나에 대한 STOMP 연결.
 *
 * - 보내기: /app/location.share/{방코드}
 * - 받기:   /topic/group/{방코드}
 * - 연결이 끊기면 3초 뒤 자동 재연결하고, 다시 연결되면 구독을 새로 건다.
 * - 끊겨 있는 동안 보내려던 내 좌표는 마지막 1건만 들고 있다가 재연결 때 보낸다.
 *   (도착 메시지가 끊긴 순간에 묻히지 않게 하기 위함)
 *
 * 모든 콜백은 메인 스레드에서 호출된다. 메서드도 메인 스레드에서 부르는 것을 전제로 한다.
 *
 * 사용 예 (도착 현황 화면):
 * <pre>
 * // onResume
 * stompManager.connect(roomCode, location -> { 하단 리스트 갱신 });
 * // onPause
 * stompManager.disconnect();
 * </pre>
 */
public class LocationStompManager {

    private static final String TAG = "STOMP";

    /** 안드로이드 에뮬레이터에서 PC(localhost)로 연결하는 주소 */
    public static final String DEFAULT_SERVER_URL = "ws://10.0.2.2:8080/ws-location";

    private static final long RECONNECT_DELAY_MS = 3000L;

    /** 다른 사람(내 메시지 포함) 위치 수신 */
    public interface OnLocationReceivedListener {
        void onLocationReceived(@NonNull MemberLocation location);
    }

    /** 연결 상태 변화. 화면에 "연결 중" 표시 등이 필요할 때만 등록 */
    public interface OnConnectionChangedListener {
        void onConnectionChanged(boolean connected);
    }

    private final String serverUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Runnable reconnectTask = this::reconnectIfNeeded;

    private StompClient stompClient;
    private String roomCode;
    private OnLocationReceivedListener locationListener;
    private OnConnectionChangedListener connectionListener;
    private MemberLocation pending;
    private boolean wantConnected;
    private boolean connected;

    public LocationStompManager() {
        this(DEFAULT_SERVER_URL);
    }

    public LocationStompManager(@NonNull String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setOnConnectionChangedListener(@Nullable OnConnectionChangedListener listener) {
        this.connectionListener = listener;
    }

    /** 방에 연결하고 위치 구독을 건다. 이미 연결돼 있으면 다시 연결한다 */
    public void connect(@NonNull String roomCode, @NonNull OnLocationReceivedListener listener) {
        String code = roomCode.toUpperCase();
        if (!code.equals(this.roomCode)) {
            pending = null; // 다른 방의 못 보낸 좌표는 버린다
        }
        this.roomCode = code;
        this.locationListener = listener;
        this.wantConnected = true;
        openConnection();
    }

    /**
     * 내 위치를 보낸다. 연결이 안 돼 있으면 마지막 1건을 들고 있다가 재연결 때 보낸다.
     */
    public void sendMyLocation(@NonNull MemberLocation location) {
        if (stompClient == null || !connected || roomCode == null) {
            pending = location;
            return;
        }
        disposables.add(stompClient.send("/app/location.share/" + roomCode, location.toJson())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Log.d(TAG, "위치 전송 완료 " + location.latitude + ", " + location.longitude
                                + (location.arrived ? " (도착)" : "")),
                        throwable -> {
                            Log.e(TAG, "위치 전송 실패", throwable);
                            if (pending == null) {
                                pending = location;
                            }
                        }));
    }

    /** 연결 해제. 자동 재연결도 멈춘다 */
    public void disconnect() {
        wantConnected = false;
        mainHandler.removeCallbacks(reconnectTask);
        closeClient();
        setConnected(false);
    }

    public boolean isConnected() {
        return connected;
    }

    private void openConnection() {
        mainHandler.removeCallbacks(reconnectTask);
        closeClient();
        setConnected(false);

        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, serverUrl);
        disposables.add(stompClient.lifecycle()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onLifecycle,
                        throwable -> Log.e(TAG, "연결 상태 수신 에러", throwable)));
        stompClient.connect();
    }

    private void onLifecycle(LifecycleEvent event) {
        switch (event.getType()) {
            case OPENED:
                Log.d(TAG, "웹소켓 연결 성공");
                setConnected(true);
                subscribeRoom();
                flushPending();
                break;
            case ERROR:
                Log.e(TAG, "웹소켓 에러", event.getException());
                setConnected(false);
                scheduleReconnect();
                break;
            case CLOSED:
            case FAILED_SERVER_HEARTBEAT:
                Log.d(TAG, "웹소켓 연결 종료: " + event.getType());
                setConnected(false);
                scheduleReconnect();
                break;
        }
    }

    private void subscribeRoom() {
        if (stompClient == null || roomCode == null) {
            return;
        }
        disposables.add(stompClient.topic("/topic/group/" + roomCode)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(message -> {
                    MemberLocation location = MemberLocation.fromJson(message.getPayload());
                    if (location == null) {
                        Log.e(TAG, "위치 메시지 형식 오류: " + message.getPayload());
                        return;
                    }
                    if (locationListener != null) {
                        locationListener.onLocationReceived(location);
                    }
                }, throwable -> Log.e(TAG, "구독 에러", throwable)));
    }

    private void flushPending() {
        if (pending != null) {
            MemberLocation toSend = pending;
            pending = null;
            sendMyLocation(toSend);
        }
    }

    private void scheduleReconnect() {
        if (!wantConnected) {
            return;
        }
        mainHandler.removeCallbacks(reconnectTask);
        mainHandler.postDelayed(reconnectTask, RECONNECT_DELAY_MS);
    }

    private void reconnectIfNeeded() {
        if (wantConnected && !connected) {
            Log.d(TAG, "재연결 시도");
            openConnection();
        }
    }

    /** 이전 연결의 구독과 소켓을 정리한다. 상태 콜백을 먼저 끊어 재연결 루프를 막는다 */
    private void closeClient() {
        disposables.clear();
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
    }

    private void setConnected(boolean value) {
        if (connected == value) {
            return;
        }
        connected = value;
        if (connectionListener != null) {
            connectionListener.onConnectionChanged(value);
        }
    }
}
