package com.example.dawaga;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.dawaga.location.LocationTracker;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirstRunActivity extends AppCompatActivity {
    private static final String LOCATION_GUIDE_PENDING = "firstRunLocationGuidePending";

    public static class Registration extends ViewModel {
        enum Status { IDLE, SAVING, SAVED, ERROR }
        final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    }

    private SharedPreferences prefs;
    private Registration registration;
    private EditText nickname;
    private Button start;
    private boolean showingPermission;
    private String draft = "";

    private final ActivityResultLauncher<String[]> requestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (LocationTracker.hasPermission(this)) {
                    completeOnboarding();
                } else {
                    Toast.makeText(this, "나중에 설정하고 홈으로 이동할 수 있어요.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);
        boolean registered = prefs.getString("userId", null) != null
                && prefs.getString("nickname", null) != null;
        if (registered && !prefs.getBoolean(LOCATION_GUIDE_PENDING, false)) {
            openHome();
            return;
        }

        registration = new ViewModelProvider(this).get(Registration.class);
        draft = prefs.getString("nickname", "");
        boolean showPermission = registered;
        if (savedInstanceState != null) {
            draft = savedInstanceState.getString("draft", draft);
            showPermission = savedInstanceState.getBoolean("showingPermission", registered);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (showingPermission) showNicknameRegistration();
                else if (registration.status.getValue() != Registration.Status.SAVING) finish();
            }
        });
        if (showPermission) showLocationGuide();
        else showNicknameRegistration();

        registration.status.observe(this, status -> {
            if (status == Registration.Status.SAVED) {
                draft = prefs.getString("nickname", "");
                showLocationGuide();
                registration.status.setValue(Registration.Status.IDLE);
            } else if (!showingPermission) {
                start.setEnabled(status != Registration.Status.SAVING);
                start.setText(status == Registration.Status.SAVING ? "등록 중…" : "시작하기");
                if (status == Registration.Status.ERROR) {
                    nickname.setError("닉네임을 저장하지 못했어요. 다시 시도해 주세요.");
                }
            }
        });
    }

    private void showNicknameRegistration() {
        showingPermission = false;
        SetupScreen screen = new SetupScreen(this, "닉네임 등록",
                "처음 한 번만 등록하면 바로 약속에 참여할 수 있어요.", null);

        LinearLayout brand = screen.column();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setBackground(screen.shape(Color.WHITE, true));
        brand.setPadding(screen.dp(20), screen.dp(24), screen.dp(20), screen.dp(24));
        brand.setMinimumHeight(screen.dp(164));
        screen.add(brand, screen.label("다와가", 42, SetupScreen.INK, true), -2, 0);
        screen.add(brand, screen.label("약속 장소에 누가 먼저 도착할까?", 15, SetupScreen.INK, false), -2, 12);
        screen.add(screen.content, brand, -2, 64);

        TextView label = screen.label("사용할 닉네임", 15, SetupScreen.INK, true);
        screen.add(screen.content, label, -2, 40);
        nickname = new EditText(this);
        nickname.setId(R.id.first_run_nickname);
        label.setLabelFor(nickname.getId());
        nickname.setSingleLine(true);
        nickname.setInputType(InputType.TYPE_CLASS_TEXT);
        nickname.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        nickname.setHint("닉네임 입력");
        nickname.setText(draft);
        nickname.setTextSize(16);
        nickname.setTextColor(SetupScreen.INK);
        nickname.setHintTextColor(SetupScreen.MUTED);
        nickname.setBackground(screen.shape(Color.WHITE, true));
        nickname.setPadding(screen.dp(16), 0, screen.dp(16), 0);
        nickname.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nickname.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            saveNickname();
            return true;
        });
        screen.add(screen.content, nickname, 56, 12);
        screen.add(screen.content, screen.label("최대 8자 · 다른 참여자에게 표시돼요", 13,
                SetupScreen.MUTED, false), -2, 12);

        start = screen.button("시작하기", SetupScreen.BLUE, this::saveNickname);
        screen.add(screen.footer, start, 56, 0);
        TextView hint = screen.label("닉네임은 설정에서 변경할 수 있어요.", 13, SetupScreen.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        screen.add(screen.footer, hint, -2, 8);
    }

    private void saveNickname() {
        if (registration.status.getValue() == Registration.Status.SAVING) return;
        draft = nickname.getText().toString().trim();
        if (draft.isEmpty()) {
            nickname.setError("닉네임을 입력해 주세요.");
            nickname.requestFocus();
            return;
        }
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(nickname.getWindowToken(), 0);
        String value = draft;
        String userId = prefs.getString("userId", null);
        if (userId != null && value.equals(prefs.getString("nickname", null))) {
            showLocationGuide();
            return;
        }
        registration.status.setValue(Registration.Status.SAVING);
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId).update("nickname", value)
                    .addOnSuccessListener(unused -> onNicknameSaved(userId, value))
                    .addOnFailureListener(error -> registration.status.setValue(Registration.Status.ERROR));
            return;
        }
        new DawagaFirebaseManager().registerUser(FirstRunActivity.this, value, new DawagaFirebaseManager.OnUserRegisteredListener() {
            @Override
            public void onSuccess(String id, String savedNickname) {
                onNicknameSaved(id, savedNickname);
            }

            @Override
            public void onFailure(Exception error) {
                registration.status.setValue(Registration.Status.ERROR);
            }
        });
    }

    private void onNicknameSaved(String userId, String value) {
        prefs.edit().putString("userId", userId).putString("nickname", value)
                .putBoolean(LOCATION_GUIDE_PENDING, true).apply();
        registration.status.setValue(Registration.Status.SAVED);
    }

    private void showLocationGuide() {
        showingPermission = true;
        nickname = null;
        SetupScreen screen = new SetupScreen(this, "위치 권한 안내",
                "실시간 도착 현황을 확인하려면 위치 권한이 필요해요.", this::showNicknameRegistration);

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(screen.shape(SetupScreen.PALE, false));
        ImageView pin = new ImageView(this);
        pin.setImageResource(R.drawable.ic_setup_pin);
        pin.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconTile.addView(pin, new FrameLayout.LayoutParams(screen.dp(54), screen.dp(54), Gravity.CENTER));
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(screen.dp(130), screen.dp(130));
        tileParams.gravity = Gravity.CENTER_HORIZONTAL;
        tileParams.topMargin = screen.dp(80);
        screen.content.addView(iconTile, tileParams);

        TextView title = screen.label("위치 권한을 허용해 주세요", 24, SetupScreen.INK, true);
        title.setGravity(Gravity.CENTER);
        screen.add(screen.content, title, -2, 38);
        TextView description = screen.label(
                "약속 10분 전부터 내 위치와 예상 도착 시간을 친구들에게 공유해요. 약속 종료 후 공유는 자동으로 중단돼요.",
                15, SetupScreen.INK, false);
        description.setGravity(Gravity.CENTER);
        description.setPadding(screen.dp(8), 0, screen.dp(8), 0);
        screen.add(screen.content, description, -2, 16);

        screen.add(screen.footer, screen.button("위치 권한 허용", SetupScreen.BLUE, this::askForLocation), 56, 0);
        Button later = screen.button("나중에 설정하기", Color.TRANSPARENT, this::completeOnboarding);
        later.setTypeface(null, android.graphics.Typeface.NORMAL);
        screen.add(screen.footer, later, 48, 2);
    }

    private void askForLocation() {
        if (LocationTracker.hasPermission(this)) {
            completeOnboarding();
            return;
        }
        requestLocation.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void completeOnboarding() {
        prefs.edit().remove(LOCATION_GUIDE_PENDING).apply();
        openHome();
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("showingPermission", showingPermission);
        outState.putString("draft", nickname == null ? draft : nickname.getText().toString());
    }
}
