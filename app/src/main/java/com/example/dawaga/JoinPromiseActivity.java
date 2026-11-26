package com.example.dawaga;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class JoinPromiseActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "DawagaPrefs";

    private TextView tvJoinTitle;
    private TextView tvJoinSubtitle;
    private TextView tvErrorWarning;
    private TextView tvJoinGuide;
    private EditText etInviteCode;
    private Button btnConfirmPromise;
    private DawagaFirebaseManager firebaseManager;

    private boolean isErrorState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_promise);

        firebaseManager = new DawagaFirebaseManager();

        ImageButton btnBack = findViewById(R.id.btn_back_join);
        tvJoinTitle = findViewById(R.id.tv_join_title);
        tvJoinSubtitle = findViewById(R.id.tv_join_subtitle);
        tvErrorWarning = findViewById(R.id.tv_error_warning);
        tvJoinGuide = findViewById(R.id.tv_join_guide);
        etInviteCode = findViewById(R.id.et_invite_code);
        btnConfirmPromise = findViewById(R.id.btn_confirm_promise);

        Uri invite = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && invite != null
                && "dawaga".equals(invite.getScheme()) && "invite".equals(invite.getHost())
                && invite.getPathSegments().size() == 1) {
            String code = invite.getLastPathSegment();
            if (code != null && code.matches("[A-Za-z0-9]{6}")) {
                etInviteCode.setText(code.toUpperCase(Locale.ROOT));
            }
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnConfirmPromise != null) {
            btnConfirmPromise.setOnClickListener(v -> {
                if (isErrorState) {
                    resetToNormalState();
                } else {
                    validateAndOpenConfirmJoin();
                }
            });
        }
    }

    private void showErrorState() {
        isErrorState = true;
        tvJoinTitle.setText("코드 오류");
        tvJoinSubtitle.setText("잘못된 코드나 만료된 초대 코드일 때 표시해요.");
        tvErrorWarning.setText("⚠️ 유효하지 않은 초대 코드예요.");
        tvErrorWarning.setVisibility(View.VISIBLE);
        tvJoinGuide.setText("코드를 다시 확인하거나 방장에게 새 코드를 요청해 주세요.");
        btnConfirmPromise.setText("다시 입력하기");
    }

    private void showAlreadyJoinedState() {
        isErrorState = true;
        tvJoinTitle.setText("초대 코드 입력");
        tvJoinSubtitle.setText("친구에게 받은 6자리 코드를 입력해 주세요.");
        tvErrorWarning.setText("⚠️ 이미 참여중인 약속입니다.");
        tvErrorWarning.setVisibility(View.VISIBLE);
        tvJoinGuide.setText("다른 약속의 초대 코드를 입력해 주세요.");
        btnConfirmPromise.setText("다시 입력하기");
    }

    private void resetToNormalState() {
        isErrorState = false;
        tvJoinTitle.setText("초대 코드 입력");
        tvJoinSubtitle.setText("친구에게 받은 6자리 코드를 입력해 주세요.");
        tvErrorWarning.setVisibility(View.GONE);
        tvJoinGuide.setText("코드가 맞으면 약속 정보를 확인할 수 있어요.");
        btnConfirmPromise.setText("참여 확인");

        if (etInviteCode != null) {
            etInviteCode.setText("");
            etInviteCode.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etInviteCode, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private boolean isAlreadyJoinedRoom(String code) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            String raw = prefs.getString("saved_promises", "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (code.equalsIgnoreCase(obj.optString("room_code"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void validateAndOpenConfirmJoin() {
        String code = (etInviteCode != null && etInviteCode.getText() != null)
                ? etInviteCode.getText().toString().trim().toUpperCase(Locale.ROOT)
                : "";

        if (TextUtils.isEmpty(code) || code.length() != 6) {
            Toast.makeText(this, "6자리 초대 코드를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isAlreadyJoinedRoom(code)) {
            showAlreadyJoinedState();
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
                    showErrorState();
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
                return new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(millis));
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