package com.example.dawaga.location;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dawaga.ArrivalStatusActivity;
import com.example.dawaga.R;

/**
 * 위치 공유 시작 대기 (피그마 B · 11).
 *
 * 약속 10분 전까지 남은 시간을 보여 주고, 시간이 되면 도착 현황 화면(ArrivalStatusActivity)으로
 * 넘어간다. 넘어가기 전에 위치 권한과 GPS를 확인해서, 막혀 있으면 권한 안내나 오류 화면을 먼저 띄운다.
 *
 * 여는 쪽 (예: 약속 대기실):
 * <pre>
 * LocationShareWaitingActivity.start(this, roomCode, meetTimeMillis, destLat, destLng, placeName);
 * </pre>
 */
public class LocationShareWaitingActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_CODE = "ROOM_CODE";
    public static final String EXTRA_MEET_TIME = "MEET_TIME";
    public static final String EXTRA_DEST_LAT = "DEST_LAT";
    public static final String EXTRA_DEST_LNG = "DEST_LNG";
    public static final String EXTRA_PLACE_NAME = "PLACE_NAME";

    private static final long TICK_MS = 1000L;

    public static void start(@NonNull Context context, @NonNull String roomCode, long meetTimeMillis,
                             double destLat, double destLng, @NonNull String placeName) {
        Intent intent = new Intent(context, LocationShareWaitingActivity.class)
                .putExtra(EXTRA_ROOM_CODE, roomCode)
                .putExtra(EXTRA_MEET_TIME, meetTimeMillis)
                .putExtra(EXTRA_DEST_LAT, destLat)
                .putExtra(EXTRA_DEST_LNG, destLng)
                .putExtra(EXTRA_PLACE_NAME, placeName);
        context.startActivity(intent);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;

    private TextView tvCountdown;
    private ProgressBar progressBar;
    private TextView tvStatusBadge;
    private TextView tvStatusDesc;

    private String roomCode;
    private long meetTimeMillis;
    private double destLat;
    private double destLng;
    private String placeName;
    private long openedAtMillis;
    private boolean moving;
    /** 권한 안내나 오류 화면이 떠 있는 중 (겹쳐 띄우지 않기 위함) */
    private boolean fixScreenOpen;
    /** 공유 시간에 해결 화면을 자동으로 한 번 띄웠는지 (닫은 뒤 다시 자동으로 띄우지 않음) */
    private boolean autoFixShown;

    /** 권한 안내나 오류 화면에서 돌아왔을 때. 해결됐으면 바로 다시 시도한다 */
    private final ActivityResultLauncher<Intent> fixLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                fixScreenOpen = false;
                updateStatusCard();
                if (result.getResultCode() == RESULT_OK && isShareTime()) {
                    moveToArrivalStatus();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_share_waiting);

        Intent intent = getIntent();
        roomCode = intent.getStringExtra(EXTRA_ROOM_CODE);
        meetTimeMillis = intent.getLongExtra(EXTRA_MEET_TIME, 0L);
        destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0);
        destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0);
        placeName = intent.getStringExtra(EXTRA_PLACE_NAME);
        if (placeName == null || placeName.isEmpty()) {
            placeName = "약속 장소";
        }
        openedAtMillis = System.currentTimeMillis();

        tvCountdown = findViewById(R.id.tv_share_countdown);
        progressBar = findViewById(R.id.progress_share_countdown);
        tvStatusBadge = findViewById(R.id.tv_share_status_badge);
        tvStatusDesc = findViewById(R.id.tv_share_status_desc);

        findViewById(R.id.btn_back_share_waiting).setOnClickListener(v -> finish());
        findViewById(R.id.card_share_status).setOnClickListener(v -> openFixScreenIfNeeded());

        // 처음 들어왔을 때 권한이 없으면 미리 받아 둔다 (공유 시간에 막히지 않도록)
        if (savedInstanceState == null
                && LocationReadiness.check(this) == LocationReadiness.State.NO_PERMISSION) {
            openFixScreenIfNeeded();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusCard();
        onTick();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void onTick() {
        long now = System.currentTimeMillis();
        long remaining = ShareCountdown.remainingMillis(now, meetTimeMillis);
        tvCountdown.setText(ShareCountdown.format(remaining));
        progressBar.setProgress(Math.round(
                ShareCountdown.progress(now, openedAtMillis, meetTimeMillis) * progressBar.getMax()));

        if (remaining <= 0) {
            moveToArrivalStatus();
            return;
        }
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
    }

    private boolean isShareTime() {
        return ShareCountdown.remainingMillis(System.currentTimeMillis(), meetTimeMillis) <= 0;
    }

    /** 공유 시간이 됐을 때. 권한과 GPS가 준비됐으면 도착 현황으로, 아니면 해결 화면으로 */
    private void moveToArrivalStatus() {
        if (moving) {
            return;
        }
        if (LocationReadiness.check(this) != LocationReadiness.State.READY) {
            updateStatusCard();
            // 자동으로는 한 번만 띄운다. 사용자가 닫았으면 상태 카드를 눌러서 다시 열 수 있다
            if (!autoFixShown) {
                autoFixShown = true;
                openFixScreenIfNeeded();
            }
            return;
        }
        moving = true;
        handler.removeCallbacks(tick);
        // ArrivalStatusActivity가 읽는 키와 같은 이름 (ROOM_CODE, DEST_LAT, DEST_LNG, PLACE_NAME)
        Intent intent = new Intent(this, ArrivalStatusActivity.class)
                .putExtra(EXTRA_ROOM_CODE, roomCode)
                .putExtra(EXTRA_DEST_LAT, destLat)
                .putExtra(EXTRA_DEST_LNG, destLng)
                .putExtra(EXTRA_PLACE_NAME, placeName)
                .putExtra(EXTRA_MEET_TIME, meetTimeMillis);
        startActivity(intent);
        finish();
    }

    private void openFixScreenIfNeeded() {
        if (fixScreenOpen) {
            return;
        }
        switch (LocationReadiness.check(this)) {
            case NO_PERMISSION:
                fixScreenOpen = true;
                fixLauncher.launch(new Intent(this, LocationPermissionActivity.class));
                break;
            case LOCATION_OFF:
                fixScreenOpen = true;
                fixLauncher.launch(new Intent(this, LocationErrorActivity.class));
                break;
            default:
                break;
        }
    }

    /** 위치 공유 상태 카드: 준비됐으면 "대기", 막혀 있으면 원인과 함께 "확인 필요" */
    private void updateStatusCard() {
        switch (LocationReadiness.check(this)) {
            case NO_PERMISSION:
                tvStatusBadge.setText("확인 필요");
                tvStatusDesc.setText("위치 권한을 허용해야 공유할 수 있어요. 눌러서 허용하기");
                break;
            case LOCATION_OFF:
                tvStatusBadge.setText("확인 필요");
                tvStatusDesc.setText("기기 위치(GPS)가 꺼져 있어요. 눌러서 확인하기");
                break;
            default:
                tvStatusBadge.setText("대기");
                tvStatusDesc.setText("아직 공유 전이에요");
                break;
        }
    }
}
