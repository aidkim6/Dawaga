package com.example.dawaga;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class JoinPromiseActivity extends AppCompatActivity {

    private ImageButton btnBackJoin;
    private EditText etInviteCode;
    private Button btnConfirmPromise;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_promise);

        btnBackJoin = findViewById(R.id.btn_back_join);
        etInviteCode = findViewById(R.id.et_invite_code);
        btnConfirmPromise = findViewById(R.id.btn_confirm_promise);

        // 소문자로 입력해도 자동으로 대문자 + 최대 6자리로 변환
        etInviteCode.setFilters(new InputFilter[]{
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(6)
        });

        // 뒤로가기 버튼
        btnBackJoin.setOnClickListener(v -> finish());

        // 약속 확인 버튼 클릭
        btnConfirmPromise.setOnClickListener(v -> {
            String code = etInviteCode.getText().toString().trim().toUpperCase();

            if (code.length() != 6) {
                Toast.makeText(this, "6자리 초대 코드를 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: DawagaFirebaseManager로 초대 코드 조회 후 약속 확인/대기실 화면 이동
            checkInviteCode(code);
        });
    }

    private void checkInviteCode(String inviteCode) {
        // 임시 확인용 토스트 (Firebase 연동 전 테스트용)
        Toast.makeText(this, "입력된 코드: " + inviteCode, Toast.LENGTH_SHORT).show();

        // 연동 후 대기실 이동 예시:
        // Intent intent = new Intent(JoinPromiseActivity.this, PromiseWaitingActivity.class);
        // intent.putExtra("roomCode", inviteCode);
        // startActivity(intent);
        // finish();
    }
}