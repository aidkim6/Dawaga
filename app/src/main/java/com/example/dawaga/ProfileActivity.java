package com.example.dawaga;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;

public class ProfileActivity extends AppCompatActivity {
    private EditText nickname;
    private Button save;
    private SharedPreferences prefs;
    private ImageView profilePhoto;
    private Button changePhoto;
    private final SharedPreferences.OnSharedPreferenceChangeListener photoListener = (preferences, key) -> {
        if (ProfilePhoto.REVISION.equals(key)) refreshPhoto();
    };
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::savePhoto);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);
        SetupScreen screen = new SetupScreen(this, "내 정보",
                "프로필 사진과 닉네임을 수정할 수 있어요.", this::finish);
        LinearLayout avatarSection = screen.column();
        avatarSection.setGravity(Gravity.CENTER_HORIZONTAL);
        profilePhoto = new ImageView(this);
        profilePhoto.setBackgroundResource(R.drawable.bg_avatar_circle);
        profilePhoto.setClipToOutline(true);
        profilePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profilePhoto.setContentDescription("프로필 사진");
        int avatarSize = screen.dp(96);
        avatarSection.addView(profilePhoto, new LinearLayout.LayoutParams(avatarSize, avatarSize));
        changePhoto = screen.button("사진 추가", SetupScreen.PALE, () -> photoPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        LinearLayout.LayoutParams photoButtonParams = new LinearLayout.LayoutParams(screen.dp(144), screen.dp(48));
        photoButtonParams.topMargin = screen.dp(12);
        avatarSection.addView(changePhoto, photoButtonParams);
        screen.add(screen.content, avatarSection, -2, 36);
        TextView label = screen.label("닉네임", 15, SetupScreen.INK, true);
        screen.add(screen.content, label, -2, 32);
        nickname = new EditText(this);
        nickname.setId(R.id.profile_nickname);
        label.setLabelFor(nickname.getId());
        nickname.setSingleLine(true);
        nickname.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nickname.setBackground(screen.shape(android.graphics.Color.WHITE, true));
        nickname.setTextColor(SetupScreen.INK);
        nickname.setHintTextColor(SetupScreen.MUTED);
        nickname.setTextSize(15);
        nickname.setPadding(screen.dp(16), 0, screen.dp(16), 0);
        nickname.setHint("내 닉네임");
        nickname.setText(prefs.getString("nickname", ""));
        screen.add(screen.content, nickname, 56, 10);
        save = screen.button("수정", SetupScreen.BLUE, this::saveNickname);
        screen.add(screen.footer, save, 56, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        prefs.registerOnSharedPreferenceChangeListener(photoListener);
        refreshPhoto();
    }

    @Override
    protected void onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(photoListener);
        super.onStop();
    }

    private void refreshPhoto() {
        changePhoto.setText(ProfilePhoto.show(profilePhoto) ? "사진 변경" : "사진 추가");
    }

    private void savePhoto(Uri uri) {
        if (uri == null) return;
        changePhoto.setEnabled(false);
        Context context = getApplicationContext();
        ProfilePhoto.IO.execute(() -> {
            boolean saved;
            try {
                ProfilePhoto.save(context, uri);
                saved = true;
            } catch (IOException | RuntimeException error) {
                saved = false;
            }
            boolean success = saved;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                changePhoto.setEnabled(true);
                if (!success) {
                    Toast.makeText(this, "사진을 저장하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveNickname() {
        String value = nickname.getText().toString().trim();
        if (value.isEmpty()) {
            nickname.setError("닉네임을 입력해 주세요.");
            return;
        }
        save.setEnabled(false);
        String userId = prefs.getString("userId", null);
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                    .update("nickname", value)
                    .addOnSuccessListener(unused -> onSaved(userId, value))
                    .addOnFailureListener(error -> showError());
            return;
        }
        new DawagaFirebaseManager().registerUser(value, new DawagaFirebaseManager.OnUserRegisteredListener() {
            @Override
            public void onSuccess(String id, String savedNickname) {
                onSaved(id, savedNickname);
            }

            @Override
            public void onFailure(Exception error) {
                showError();
            }
        });
    }

    private void onSaved(String userId, String value) {
        prefs.edit().putString("userId", userId).putString("nickname", value).apply();
        if (!isDestroyed()) finish();
    }

    private void showError() {
        if (isDestroyed()) return;
        save.setEnabled(true);
        nickname.setError("닉네임을 저장하지 못했어요. 다시 시도해 주세요.");
    }
}
