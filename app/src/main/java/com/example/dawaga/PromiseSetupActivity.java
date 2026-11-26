package com.example.dawaga;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.KakaoMapSdk;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PromiseSetupActivity extends AppCompatActivity {
    public static class SetupState extends ViewModel {
        final MutableLiveData<String> roomCode = new MutableLiveData<>();
        final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
        final MutableLiveData<Boolean> failed = new MutableLiveData<>(false);
    }

    private record Place(String name, String address, double latitude, double longitude) {}

    private SetupState state;
    private SetupScreen screen;
    private MapView mapView;
    private KakaoMap map;
    private Label pin;
    private LabelStyles pinStyles;
    private LinearLayout results;
    private EditText query;
    private Button confirm;
    private Place selected;
    private String roomCode;
    private String searchText = "";
    private int searchVersion;
    private final ExecutorService geocoding = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        state = new ViewModelProvider(this).get(SetupState.class);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
        if (saved != null) {
            searchText = saved.getString("query", "");
            roomCode = saved.getString("code");
            if (saved.containsKey("place")) {
                selected = new Place(saved.getString("place"), saved.getString("address"),
                        saved.getDouble("latitude"), saved.getDouble("longitude"));
            }
        }
        if (roomCode == null) showPlaceSelection();
        else showInvite();

        state.roomCode.observe(this, code -> {
            if (!code.equals(roomCode)) {
                roomCode = code;
                showInvite();
            }
        });
        state.saving.observe(this, saving -> {
            if (roomCode == null) {
                query.setEnabled(!saving);
                confirm.setEnabled(!saving && selected != null);
                confirm.setText(saving ? "약속 만드는 중…" : "이 장소로 정하기");
            }
        });
        state.failed.observe(this, failed -> {
            if (failed) {
                Toast.makeText(this, "약속을 만들지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show();
                state.failed.setValue(false);
            }
        });
    }

    private void showPlaceSelection() {
        screen = new SetupScreen(this, "장소 선택", "친구들이 모일 정확한 약속 장소를 선택해 주세요.", this::goBack);
        LinearLayout search = new LinearLayout(this);
        search.setBackground(screen.shape(Color.WHITE, true));
        search.setGravity(Gravity.CENTER_VERTICAL);
        query = new EditText(this);
        query.setId(R.id.promise_place_query);
        query.setSingleLine(true);
        query.setTextSize(15);
        query.setBackgroundColor(Color.TRANSPARENT);
        query.setPadding(screen.dp(16), 0, 0, 0);
        query.setTextColor(SetupScreen.INK);
        query.setHintTextColor(SetupScreen.MUTED);
        query.setHint("장소명 또는 주소 검색");
        query.setText(searchText);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            searchPlaces();
            return true;
        });
        search.addView(query, new LinearLayout.LayoutParams(0, -1, 1));
        search.addView(screen.button("검색", SetupScreen.PALE, this::searchPlaces),
                new LinearLayout.LayoutParams(screen.dp(66), screen.dp(48)));
        screen.add(screen.content, search, 56, 54);

        mapView = new MapView(this);
        mapView.setBackgroundColor(0xffe5ebf5);
        screen.add(screen.content, mapView, 244, 16);
        initMap();
        results = screen.column();
        screen.add(screen.content, results, -2, 24);
        if (selected != null) renderPlaces(Collections.singletonList(selected));

        confirm = screen.button("이 장소로 정하기", SetupScreen.BLUE, this::createPromise);
        confirm.setEnabled(selected != null && !Boolean.TRUE.equals(state.saving.getValue()));
        screen.add(screen.footer, confirm, 56, 0);
    }

    private void initMap() {
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
        mapView.start(new MapLifeCycleCallback() {
            @Override public void onMapDestroy() {}

            @Override public void onMapError(Exception error) {
                if (mapView != null) {
                    mapView.setContentDescription("지도를 불러오지 못했어요.");
                }
            }
        }, new KakaoMapReadyCallback() {
            @Override public void onMapReady(@NonNull KakaoMap kakaoMap) {
                if (mapView == null || isDestroyed()) return;
                map = kakaoMap;
                Drawable icon = getDrawable(R.drawable.ic_setup_pin);
                Bitmap bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
                icon.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                icon.draw(new Canvas(bitmap));
                pinStyles = map.getLabelManager().addLabelStyles(LabelStyles.from(
                        LabelStyle.from(bitmap).setAnchorPoint(0.5f, 1f)));
                if (selected == null) {
                    map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(37.5665, 126.9780), 12));
                } else {
                    updatePin();
                }
            }
        });
    }

    private void searchPlaces() {
        if (Boolean.TRUE.equals(state.saving.getValue())) return;
        String term = query.getText().toString().trim();
        if (term.isEmpty()) {
            query.setError("장소명 또는 주소를 입력해 주세요.");
            return;
        }
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
        searchText = term;
        selected = null;
        if (pin != null) {
            pin.remove();
            pin = null;
        }
        confirm.setEnabled(false);
        showSearchMessage("장소를 검색하는 중…");
        int version = ++searchVersion;
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        if (!Geocoder.isPresent()) {
            showSearchMessage("이 기기에서 장소 검색을 사용할 수 없어요.");
            return;
        }
        geocoding.execute(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocationName(term, 3);
                List<Place> places = new ArrayList<>();
                if (addresses != null) {
                    for (Address address : addresses) {
                        if (address.hasLatitude() && address.hasLongitude()) {
                            places.add(new Place(term, address.getAddressLine(0),
                                    address.getLatitude(), address.getLongitude()));
                        }
                    }
                }
                runOnUiThread(() -> {
                    if (!isDestroyed() && version == searchVersion) renderPlaces(places);
                });
            } catch (IOException | IllegalArgumentException error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && version == searchVersion) {
                        showSearchMessage("검색에 실패했어요. 주소를 확인하고 다시 시도해 주세요.");
                    }
                });
            }
        });
    }

    private void showSearchMessage(String text) {
        results.removeAllViews();
        screen.add(results, screen.label(text, 14, SetupScreen.MUTED, false), -2, 0);
    }

    private void renderPlaces(List<Place> places) {
        results.removeAllViews();
        if (places.isEmpty()) {
            showSearchMessage("검색 결과가 없어요. 도로명 주소로 다시 검색해 보세요.");
            return;
        }
        for (Place place : places) {
            LinearLayout card = screen.column();
            card.setBackground(screen.shape(Color.WHITE, true));
            card.setPadding(screen.dp(16), screen.dp(12), screen.dp(16), screen.dp(8));
            screen.add(card, screen.label("검색 결과 · " + place.name(), 16, SetupScreen.INK, true), -2, 0);
            screen.add(card, screen.label(place.address(), 13, SetupScreen.MUTED, false), -2, 6);
            Button select = screen.button(place.equals(selected) ? "선택됨" : "선택", SetupScreen.PALE,
                    () -> selectPlace(place, places));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(screen.dp(80), screen.dp(48));
            params.gravity = Gravity.END;
            card.addView(select, params);
            screen.add(results, card, -2, results.getChildCount() == 0 ? 0 : 10);
        }
    }

    private void selectPlace(Place place, List<Place> places) {
        if (Boolean.TRUE.equals(state.saving.getValue())) return;
        selected = place;
        updatePin();
        confirm.setEnabled(true);
        renderPlaces(places);
    }

    private void updatePin() {
        if (map == null || map.getLabelManager() == null || map.getLabelManager().getLayer() == null) return;
        LatLng point = LatLng.from(selected.latitude(), selected.longitude());
        if (pin != null) pin.remove();
        pin = map.getLabelManager().getLayer().addLabel(LabelOptions.from(point).setStyles(pinStyles));
        map.moveCamera(CameraUpdateFactory.newCenterPosition(point, 16));
    }

    private void createPromise() {
        if (selected == null || Boolean.TRUE.equals(state.saving.getValue())) return;
        state.saving.setValue(true);
        String title = getIntent().getStringExtra("promise_title");
        String date = getIntent().getStringExtra("promise_date");
        String time = getIntent().getStringExtra("promise_time");
        String placeName = selected.name();

        new DawagaFirebaseManager().createRoom(title, selected.latitude(), selected.longitude(),
                getIntent().getLongExtra("meet_time", 0), new DawagaFirebaseManager.OnRoomCallback() {
                    @Override
                    public void onSuccess(Map<String, Object> roomData) {
                        String createdCode = (String) roomData.get("roomCode");
                        saveCreatedPromiseToPrefs(createdCode, title, date, time, placeName);
                        state.roomCode.setValue(createdCode);
                        state.saving.setValue(false);
                    }

                    @Override
                    public void onFailure(Exception error) {
                        state.saving.setValue(false);
                        state.failed.setValue(true);
                    }
                });
    }

    private void saveCreatedPromiseToPrefs(String code, String title, String date, String time, String place) {
        if (code == null || code.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);
        try {
            String raw = prefs.getString("saved_promises", "[]");
            JSONArray arr = new JSONArray(raw);
            JSONArray updated = new JSONArray();

            JSONObject newItem = new JSONObject();
            newItem.put("room_code", code.trim().toUpperCase(Locale.ROOT));
            newItem.put("promise_title", title != null ? title : "약속");
            newItem.put("promise_date", date != null ? date : "");
            newItem.put("promise_time", time != null ? time : "");
            newItem.put("promise_place", place != null ? place : "");
            updated.put(newItem);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!code.equalsIgnoreCase(obj.optString("room_code"))) {
                    updated.put(obj);
                }
            }
            prefs.edit().putString("saved_promises", updated.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void showInvite() {
        if (mapView != null) {
            MapView placeMap = mapView;
            mapView = null;
            map = null;
            placeMap.finish();
        }
        screen = new SetupScreen(this, "초대 코드 생성", "약속이 만들어졌어요. 친구들에게 코드를 공유해 주세요.",
                this::goBack);
        LinearLayout codeCard = screen.column();
        codeCard.setBackground(screen.shape(Color.WHITE, true));
        codeCard.setGravity(Gravity.CENTER_HORIZONTAL);
        codeCard.setPadding(screen.dp(16), screen.dp(24), screen.dp(16), screen.dp(28));
        codeCard.addView(screen.label("초대 코드", 14, SetupScreen.MUTED, true));
        TextView code = screen.label(roomCode, 40, SetupScreen.INK, true);
        code.setGravity(Gravity.CENTER);
        code.setLetterSpacing(0.08f);
        screen.add(codeCard, code, -2, 24);
        Button copy = screen.button("코드 복사", SetupScreen.PALE, () -> copyCode(this, roomCode));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(screen.dp(132), screen.dp(48));
        copyParams.topMargin = screen.dp(24);
        codeCard.addView(copy, copyParams);
        screen.add(screen.content, codeCard, -2, 56);

        LinearLayout summary = screen.column();
        summary.setBackground(screen.shape(Color.WHITE, true));
        summary.setPadding(screen.dp(16), screen.dp(16), screen.dp(16), screen.dp(16));
        screen.add(summary, screen.label(getIntent().getStringExtra("promise_title"), 18, SetupScreen.INK, true), -2, 0);
        String details = getIntent().getStringExtra("promise_date") + " " + getIntent().getStringExtra("promise_time")
                + " · " + selected.name();
        screen.add(summary, screen.label(details, 14, SetupScreen.MUTED, false), -2, 8);
        screen.add(screen.content, summary, -2, 20);
        screen.add(screen.content, screen.button("친구에게 공유하기", SetupScreen.BLUE, this::showShareOptions), 56, 28);
        screen.add(screen.content, screen.button("약속 대기실로 이동", SetupScreen.BLUE, () -> finishWithResult(true)), 56, 12);
        screen.add(screen.content, screen.button("홈으로 이동", Color.WHITE, () -> finishWithResult(false)), 56, 12);
        TextView hint = screen.label("참여자는 코드로 입장할 수 있어요.", 13, SetupScreen.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        screen.add(screen.content, hint, -2, 12);
    }

    static void copyCode(Context context, String code) {
        context.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("초대 코드", code));
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, "초대 코드를 복사했어요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showShareOptions() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = screen.column();
        panel.setPadding(screen.dp(20), screen.dp(28), screen.dp(20), screen.dp(36));
        screen.add(panel, screen.label("친구에게 약속 공유하기", 20, SetupScreen.INK, true), -2, 0);
        screen.add(panel, screen.label("초대 코드 " + roomCode + "을 원하는 방법으로 전달해 주세요.",
                14, SetupScreen.MUTED, false), -2, 12);

        String link = new Uri.Builder().scheme("dawaga").authority("invite").appendPath(roomCode).build().toString();
        screen.add(panel, screen.button("카카오톡으로 공유", Color.WHITE, () -> {
            try {
                startActivity(inviteShareIntent().setPackage("com.kakao.talk"));
                dialog.dismiss();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "카카오톡을 사용할 수 없어요. 다른 공유 방법을 선택해 주세요.",
                        Toast.LENGTH_SHORT).show();
            }
        }), 52, 22);
        screen.add(panel, screen.button("초대 링크 복사", Color.WHITE, () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("초대 링크", link));
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(this, "초대 링크를 복사했어요.", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        }), 52, 12);
        screen.add(panel, screen.button("다른 앱으로 공유", Color.WHITE, () -> {
            startActivity(Intent.createChooser(inviteShareIntent(), "초대 코드 공유"));
            dialog.dismiss();
        }), 52, 12);
        Button cancel = screen.button("취소", Color.WHITE, dialog::dismiss);
        cancel.setTypeface(null, android.graphics.Typeface.NORMAL);
        screen.add(panel, cancel, 52, 12);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(screen.shape(SetupScreen.BACKGROUND, false));
        scroll.setClipToOutline(true);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        int width = Math.min(screen.dp(360), getResources().getDisplayMetrics().widthPixels - screen.dp(48));
        dialog.getWindow().setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private Intent inviteShareIntent() {
        return new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getIntent().getStringExtra("promise_title")
                        + " · 다와가 초대 코드 " + roomCode);
    }

    private void goBack() {
        if (roomCode != null) finishWithResult(false);
        else if (!Boolean.TRUE.equals(state.saving.getValue())) finish();
    }

    private void finishWithResult(boolean openWaiting) {
        setResult(RESULT_OK, new Intent().putExtras(getIntent())
                .putExtra("promise_place", selected.name())
                .putExtra("room_code", roomCode)
                .putExtra("open_waiting", openWaiting));
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (query != null) out.putString("query", query.getText().toString());
        out.putString("code", roomCode);
        if (selected != null) {
            out.putString("place", selected.name());
            out.putString("address", selected.address());
            out.putDouble("latitude", selected.latitude());
            out.putDouble("longitude", selected.longitude());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.resume();
    }

    @Override protected void onPause() {
        if (mapView != null) mapView.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.finish();
        geocoding.shutdownNow();
        super.onDestroy();
    }
}