package com.example.dawaga.location;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dawaga.R;

/**
 * 위치 권한 안내 (피그마 B · 02).
 *
 * 결과: 권한을 받으면 RESULT_OK, "나중에 설정하기"나 뒤로 가기면 RESULT_CANCELED.
 * 권한을 거부하면 위치 권한 / GPS 오류 화면을 띄우고 RESULT_CANCELED로 닫힌다.
 */
public class LocationPermissionActivity extends AppCompatActivity {

    private LocationPermissionHelper permissionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_permission);

        // registerForActivityResult 제약 때문에 onCreate에서 만든다
        permissionHelper = new LocationPermissionHelper(this);

        findViewById(R.id.btn_back_permission).setOnClickListener(v -> closeWithoutPermission());
        findViewById(R.id.btn_permission_later).setOnClickListener(v -> closeWithoutPermission());
        findViewById(R.id.btn_allow_location).setOnClickListener(v -> requestPermission());
    }

    private void requestPermission() {
        permissionHelper.request((granted, precise) -> {
            if (!granted) {
                // 거부했거나 "다시 묻지 않음" 상태. 설정으로 안내하는 오류 화면으로 넘긴다
                startActivity(new Intent(this, LocationErrorActivity.class));
                closeWithoutPermission();
                return;
            }
            if (!precise) {
                Toast.makeText(this,
                        "정확한 위치를 허용해야 도착 판정이 정확해요.",
                        Toast.LENGTH_LONG).show();
            }
            setResult(RESULT_OK);
            finish();
        });
    }

    private void closeWithoutPermission() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
