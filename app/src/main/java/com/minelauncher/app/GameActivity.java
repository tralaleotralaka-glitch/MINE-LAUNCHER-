package com.minelauncher.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.minelauncher.app.game.BlockTypes;
import com.minelauncher.app.game.GameSurfaceView;
import com.minelauncher.app.game.HudView;

/**
 * The actual game screen: full screen OpenGL world with touch controls.
 */
public class GameActivity extends Activity implements GameSurfaceView.Listener {

    public static final String EXTRA_WORLD_NAME = "world_name";
    public static final String EXTRA_SEED = "seed";
    public static final String EXTRA_GAMEMODE = "gamemode";
    public static final String EXTRA_NICKNAME = "nickname";
    public static final String EXTRA_VERSION = "version";

    private GameSurfaceView surface;
    private HudView hud;
    private TextView infoChip;
    private TextView fpsChip;
    private View loadingOverlay;
    private TextView loadingText;
    private View pauseOverlay;
    private TextView pauseTitle;
    private LinearLayout hotbar;
    private Button flyButton;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private int selectedBlock = BlockTypes.HOTBAR[0];
    private int gamemode = GameSurfaceView.MODE_SURVIVAL;
    private String worldName = "Новый мир";
    private String nickname = "Steve";
    private String version = "1.21.4";
    private boolean showFps = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyFullscreen();

        long seed = System.nanoTime();
        Intent in = getIntent();
        if (in != null) {
            String n = in.getStringExtra(EXTRA_WORLD_NAME);
            if (n != null && n.length() > 0) worldName = n;
            seed = in.getLongExtra(EXTRA_SEED, seed);
            gamemode = in.getIntExtra(EXTRA_GAMEMODE, GameSurfaceView.MODE_SURVIVAL);
            String nickExt = in.getStringExtra(EXTRA_NICKNAME);
            if (nickExt != null && nickExt.length() > 0) nickname = nickExt;
            String verExt = in.getStringExtra(EXTRA_VERSION);
            if (verExt != null && verExt.length() > 0) version = verExt;
        }
        showFps = Prefs.showFps(this);

        surface = (GameSurfaceView) findViewById(R.id.surface);
        hud = (HudView) findViewById(R.id.hud);
        infoChip = (TextView) findViewById(R.id.info_chip);
        fpsChip = (TextView) findViewById(R.id.fps_chip);
        loadingOverlay = findViewById(R.id.loading_overlay);
        loadingText = (TextView) findViewById(R.id.loading_text);
        pauseOverlay = findViewById(R.id.pause_overlay);
        pauseTitle = (TextView) findViewById(R.id.pause_title);
        hotbar = (LinearLayout) findViewById(R.id.hotbar);
        flyButton = (Button) findViewById(R.id.btn_fly);

        hud.setGame(surface);
        surface.setListener(this);

        float sens = 0.04f + Prefs.sensitivity(this) / 100f * 0.28f;
        surface.sensitivity = sens;

        infoChip.setText(modeName(gamemode) + " · " + version + " · " + nickname);
        loadingText.setText("Генерация мира «" + worldName + "»…");
        fpsChip.setVisibility(showFps ? View.VISIBLE : View.GONE);

        buildHotbar();
        wireButtons();

        surface.createWorld(seed, gamemode);
    }

    private void applyFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private static String modeName(int mode) {
        switch (mode) {
            case GameSurfaceView.MODE_CREATIVE: return "Творческий";
            case GameSurfaceView.MODE_ADVENTURE: return "Приключение";
            case GameSurfaceView.MODE_HARDCORE: return "Хардкор";
            default: return "Выживание";
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildHotbar() {
        hotbar.removeAllViews();
        int size = dp(44);
        for (int i = 0; i < BlockTypes.HOTBAR.length; i++) {
            final int id = BlockTypes.HOTBAR[i];
            FrameLayout item = new FrameLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(3), 0, dp(3), 0);
            item.setLayoutParams(params);
            item.setBackgroundResource(R.drawable.hotbar_item);
            item.setTag(id);

            View color = new View(this);
            int innerSize = size - dp(14);
            FrameLayout.LayoutParams colorParams = new FrameLayout.LayoutParams(innerSize, innerSize);
            colorParams.gravity = Gravity.CENTER;
            color.setLayoutParams(colorParams);
            color.setBackgroundColor(BlockTypes.color(id));
            item.addView(color);

            item.setSelected(id == selectedBlock);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedBlock = id;
                    refreshHotbar();
                }
            });
            hotbar.addView(item);
        }
    }

    private void refreshHotbar() {
        for (int i = 0; i < hotbar.getChildCount(); i++) {
            View child = hotbar.getChildAt(i);
            Object tag = child.getTag();
            child.setSelected(tag instanceof Integer && (Integer) tag == selectedBlock);
        }
    }

    private void wireButtons() {
        Button jump = (Button) findViewById(R.id.btn_jump);
        jump.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        surface.setJumpHeld(true);
                        v.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        surface.setJumpHeld(false);
                        v.setPressed(false);
                        return true;
                    default:
                        return false;
                }
            }
        });

        Button down = (Button) findViewById(R.id.btn_down);
        down.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        surface.setDownHeld(true);
                        v.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        surface.setDownHeld(false);
                        v.setPressed(false);
                        return true;
                    default:
                        return false;
                }
            }
        });

        ((Button) findViewById(R.id.btn_break)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                surface.breakBlock();
            }
        });

        ((Button) findViewById(R.id.btn_place)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                surface.placeBlock(selectedBlock);
            }
        });

        flyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!surface.toggleFly()) {
                    Toast.makeText(GameActivity.this, "Полёт доступен только в творческом режиме",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                flyButton.setSelected(surface.isFlying());
                Toast.makeText(GameActivity.this, surface.isFlying() ? "Полёт включён" : "Полёт выключен",
                        Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.btn_pause)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPause();
            }
        });

        ((Button) findViewById(R.id.btn_resume)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePause();
            }
        });

        ((Button) findViewById(R.id.btn_quit)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(GameActivity.this, MainMenuActivity.class);
                i.putExtra("nickname", nickname);
                i.putExtra("version", version);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            }
        });
    }

    private void showPause() {
        if (pauseTitle != null) pauseTitle.setText(worldName);
        pauseOverlay.setVisibility(View.VISIBLE);
        surface.setJumpHeld(false);
        surface.setDownHeld(false);
    }

    private void hidePause() {
        pauseOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (pauseOverlay.getVisibility() == View.VISIBLE) {
            hidePause();
        } else {
            showPause();
        }
    }

    // -------------------------------------------------------------- callbacks

    @Override
    public void onWorldReady() {
        loadingOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onFps(int fps) {
        if (!showFps) return;
        fpsChip.setText("FPS " + fps);
    }

    // --------------------------------------------------------------- lifecycle

    private final Runnable hudTick = new Runnable() {
        @Override
        public void run() {
            hud.invalidate();
            handler.postDelayed(this, 40);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        applyFullscreen();
        surface.onResume();
        handler.post(hudTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(hudTick);
        surface.setJumpHeld(false);
        surface.setDownHeld(false);
        surface.onPause();
    }
}
