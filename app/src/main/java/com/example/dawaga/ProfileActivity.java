package com.example.dawaga;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {
    private EditText nickname;
    private Button save;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);
        SetupScreen screen = new SetupScreen(this, "내 정보",
                "사용할 닉네임을 확인하고 수정할 수 있어요.", this::finish);
        TextView label = screen.label("닉네임", 15, SetupScreen.INK, true);
        screen.add(screen.content, label, -2, 92);
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
