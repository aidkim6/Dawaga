package com.example.dawaga;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Map;

public class JoinPromiseActivity extends AppCompatActivity {

    private ImageButton btnBackJoin;
    private EditText etInviteCode;
    private Button btnConfirmPromise;
    private DawagaFirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_promise);

        firebaseManager = new DawagaFirebaseManager();

        btnBackJoin = findViewById(R.id.btn_back_join);
        etInviteCode = findViewById(R.id.et_invite_code);
        btnConfirmPromise = findViewById(R.id.btn_confirm_promise);

        etInviteCode.setFilters(new InputFilter[]{
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(6)
        });

        btnBackJoin.setOnClickListener(v -> finish());

        btnConfirmPromise.setOnClickListener(v -> {
            String code = etInviteCode.getText().toString().trim().toUpperCase(Locale.ROOT);

            if (code.length() != 6) {
                Toast.makeText(this, "6자리 초대 코드를 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            checkInviteCode(code);
        });
    }

    private void checkInviteCode(String inviteCode) {
        btnConfirmPromise.setEnabled(false);

        firebaseManager.joinRoom(inviteCode, new DawagaFirebaseManager.OnRoomCallback() {
            @Override
            public void onSuccess(Map<String, Object> roomData) {
                btnConfirmPromise.setEnabled(true);

                String roomName = roomData.get("roomName") != null
                        ? String.valueOf(roomData.get("roomName")) : "약속 방";
                long meetTime = roomData.get("meetTime") instanceof Number
                        ? ((Number) roomData.get("meetTime")).longValue() : 0L;
                double destLat = roomData.get("destLatitude") instanceof Number
                        ? ((Number) roomData.get("destLatitude")).doubleValue() : 0.0;
                double destLng = roomData.get("destLongitude") instanceof Number
                        ? ((Number) roomData.get("destLongitude")).doubleValue() : 0.0;

                Intent intent = new Intent(JoinPromiseActivity.this, ConfirmJoinActivity.class);
                intent.putExtra("room_code", inviteCode);
                intent.putExtra("room_name", roomName);
                intent.putExtra("meet_time", meetTime);
                intent.putExtra("dest_lat", destLat);
                intent.putExtra("dest_lng", destLng);
                startActivity(intent);
            }

            @Override
            public void onFailure(Exception e) {
                btnConfirmPromise.setEnabled(true);
                Toast.makeText(JoinPromiseActivity.this,
                        "유효하지 않은 초대 코드이거나 조회에 실패했습니다.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}