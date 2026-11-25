package com.minelauncher.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Launcher screen: nickname + version selection + big PLAY button.
 * Pressing PLAY "launches" the game and teleports the player to the Minecraft-style main menu.
 */
public class LauncherActivity extends Activity {

    private static final String[] STEPS = {
            "Проверка файлов игры…",
            "Загрузка библиотек…",
            "Инициализация OpenGL ES…",
            "Подготовка ресурсов…",
            "Запуск игрового клиента…"
    };

    private EditText nicknameInput;
    private Spinner versionSpinner;
    private Button playButton;
    private View loadingBox;
    private ProgressBar progress;
    private TextView status;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int progressValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        nicknameInput = (EditText) findViewById(R.id.nickname);
        versionSpinner = (Spinner) findViewById(R.id.version_spinner);
        playButton = (Button) findViewById(R.id.play_button);
        loadingBox = findViewById(R.id.loading_box);
        progress = (ProgressBar) findViewById(R.id.progress);
        status = (TextView) findViewById(R.id.status);

        nicknameInput.setText(Prefs.nickname(this));

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.versions, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        versionSpinner.setAdapter(adapter);
        int idx = Prefs.versionIndex(this);
        if (idx >= 0 && idx < adapter.getCount()) versionSpinner.setSelection(idx);

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLaunch();
            }
        });
    }

    private String selectedVersion() {
        Object item = versionSpinner.getSelectedItem();
        String text = item == null ? "1.21.4" : item.toString();
        int dash = text.indexOf('—');
        return (dash > 0 ? text.substring(0, dash) : text).trim();
    }

    private void startLaunch() {
        hideKeyboard();

        String nick = nicknameInput.getText().toString().trim();
        if (nick.length() == 0) nick = "Steve";
        Prefs.setNickname(this, nick);
        Prefs.setVersionIndex(this, versionSpinner.getSelectedItemPosition());

        playButton.setEnabled(false);
        playButton.setText("ЗАПУСК…");
        loadingBox.setVisibility(View.VISIBLE);
        progressValue = 0;
        progress.setProgress(0);
        status.setText(STEPS[0]);
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            progressValue += 4 + (int) (Math.random() * 9);
            if (progressValue > 100) progressValue = 100;
            progress.setProgress(progressValue);
            int step = Math.min(STEPS.length - 1, progressValue * STEPS.length / 100);
            status.setText(STEPS[step] + "  " + progressValue + "%");
            if (progressValue >= 100) {
                handler.postDelayed(goToMenu, 260);
            } else {
                handler.postDelayed(this, 110);
            }
        }
    };

    private final Runnable goToMenu = new Runnable() {
        @Override
        public void run() {
            playButton.setEnabled(true);
            playButton.setText(R.string.play);
            loadingBox.setVisibility(View.GONE);
            Intent i = new Intent(LauncherActivity.this, MainMenuActivity.class);
            i.putExtra("nickname", nicknameInput.getText().toString().trim());
            i.putExtra("version", selectedVersion());
            startActivity(i);
        }
    };

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        nicknameInput.clearFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(goToMenu);
    }
}
