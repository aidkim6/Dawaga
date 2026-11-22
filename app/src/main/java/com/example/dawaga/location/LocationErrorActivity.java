package com.example.dawaga.location;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dawaga.R;

/**
 * 위치 권한 / GPS 오류 (피그마 B · 15).
 *
 * "위치 설정 확인"은 막힌 원인에 맞는 설정 화면을 연다 (권한 없음 → 앱 설정, GPS 꺼짐 → 위치 설정).
 * 설정에서 돌아왔을 때나 "다시 시도"를 눌렀을 때 문제가 풀렸으면 RESULT_OK로 닫힌다.
 */
public class LocationErrorActivity extends AppCompatActivity {

    private TextView tvHint;
    private boolean openedSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_error);

        tvHint = findViewById(R.id.tv_location_error_hint);

        findViewById(R.id.btn_back_location_error).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.btn_open_location_settings).setOnClickListener(v -> openSettings());
        findViewById(R.id.btn_location_retry).setOnClickListener(v -> {
            if (!finishIfReady()) {
                Toast.makeText(this, "아직 위치를 확인할 수 없어요.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (openedSettings) {
            openedSettings = false;
            if (finishIfReady()) {
                return;
            }
        }
        updateHint();
    }

    private void openSettings() {
        LocationReadiness.State state = LocationReadiness.check(this);
        if (state == LocationReadiness.State.READY) {
            finishIfReady();
            return;
        }
        try {
            if (state == LocationReadiness.State.NO_PERMISSION) {
                startActivity(LocationReadiness.appSettingsIntent(this));
            } else {
                startActivity(LocationReadiness.locationSettingsIntent());
            }
            openedSettings = true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "설정 화면을 열 수 없어요. 기기 설정에서 직접 확인해 주세요.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** 권한과 GPS가 모두 준비됐으면 RESULT_OK로 닫고 true */
    private boolean finishIfReady() {
        if (LocationReadiness.check(this) != LocationReadiness.State.READY) {
            return false;
        }
        setResult(RESULT_OK);
        finish();
        return true;
    }

    /** 안내 카드 문구를 지금 막힌 원인에 맞춘다 */
    private void updateHint() {
        switch (LocationReadiness.check(this)) {
            case NO_PERMISSION:
                tvHint.setText("앱 설정 > 권한에서 위치를 허용해 주세요.");
                break;
            case LOCATION_OFF:
                tvHint.setText("기기 설정에서 위치(GPS)를 켜 주세요.");
                break;
            default:
                tvHint.setText("기기 설정에서 GPS와 앱 권한을 확인해 주세요.");
                break;
        }
    }
}
