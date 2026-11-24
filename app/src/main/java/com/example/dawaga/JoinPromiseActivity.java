package com.example.dawaga;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class JoinPromiseActivity extends AppCompatActivity {

    private EditText etInviteCode;
    private Button btnConfirmPromise;
    private DawagaFirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_promise);

        firebaseManager = new DawagaFirebaseManager();

        ImageButton btnBack = findViewById(R.id.btn_back_join);
        etInviteCode = findViewById(R.id.et_invite_code);
        btnConfirmPromise = findViewById(R.id.btn_confirm_promise);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnConfirmPromise != null) {
            btnConfirmPromise.setOnClickListener(v -> validateAndOpenConfirmJoin());
        }
    }

    private void validateAndOpenConfirmJoin() {
        String code = (etInviteCode != null && etInviteCode.getText() != null)
                ? etInviteCode.getText().toString().trim().toUpperCase(Locale.ROOT)
                : "";

        if (TextUtils.isEmpty(code) || code.length() != 6) {
            Toast.makeText(this, "6자리 초대 코드를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnConfirmPromise != null) {
            btnConfirmPromise.setEnabled(false);
        }

        firebaseManager.joinRoom(code, new DawagaFirebaseManager.OnRoomCallback() {
            @Override
            public void onSuccess(Map<String, Object> roomData) {
                runOnUiThread(() -> {
                    if (btnConfirmPromise != null) {
                        btnConfirmPromise.setEnabled(true);
                    }

                    String title = getRoomTitle(roomData);
                    String date = getRoomDate(roomData);
                    String time = getRoomTime(roomData);
                    String place = getStringValue(roomData, "place", "");

                    Intent intent = new Intent(JoinPromiseActivity.this, ConfirmJoinActivity.class);
                    intent.putExtra("room_code", code);
                    intent.putExtra("promise_title", title);
                    intent.putExtra("promise_date", date);
                    intent.putExtra("promise_time", time);
                    intent.putExtra("promise_place", place);
                    intent.putStringArrayListExtra("participants", new ArrayList<>());
                    startActivity(intent);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    if (btnConfirmPromise != null) {
                        btnConfirmPromise.setEnabled(true);
                    }
                    String msg = (e != null && e.getMessage() != null && !e.getMessage().isEmpty())
                            ? e.getMessage()
                            : "약속 정보를 불러오지 못했습니다.";
                    Toast.makeText(JoinPromiseActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String getRoomTitle(Map<String, Object> roomData) {
        String roomName = getStringValue(roomData, "roomName", "");
        if (!roomName.isEmpty()) return roomName;
        return getStringValue(roomData, "title", "약속");
    }

    private String getRoomDate(Map<String, Object> roomData) {
        String date = getStringValue(roomData, "date", "");
        if (!date.isEmpty()) return date;

        if (roomData != null && roomData.get("meetTime") instanceof Number) {
            long millis = ((Number) roomData.get("meetTime")).longValue();
            if (millis > 0L) {
                return new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(new Date(millis));
            }
        }
        return "";
    }

    private String getRoomTime(Map<String, Object> roomData) {
        String time = getStringValue(roomData, "time", "");
        if (!time.isEmpty()) return time;

        if (roomData != null && roomData.get("meetTime") instanceof Number) {
            long millis = ((Number) roomData.get("meetTime")).longValue();
            if (millis > 0L) {
                return new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(millis));
            }
        }
        return "";
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || map.get(key) == null) return defaultValue;
        String value = String.valueOf(map.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }
}