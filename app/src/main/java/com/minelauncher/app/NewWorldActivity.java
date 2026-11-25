package com.minelauncher.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.minelauncher.app.game.BlockTypes;
import com.minelauncher.app.game.GameSurfaceView;

/**
 * "Create new world" screen: world name, seed and game mode selection.
 */
public class NewWorldActivity extends Activity {

    private int gamemode = GameSurfaceView.MODE_SURVIVAL;
    private String nickname = "Steve";
    private String version = "1.21.4";

    private Button[] modeButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_world);

        Intent in = getIntent();
        if (in != null) {
            String n = in.getStringExtra("nickname");
            if (n != null && n.length() > 0) nickname = n;
            String v = in.getStringExtra("version");
            if (v != null && v.length() > 0) version = v;
        }

        final EditText worldName = (EditText) findViewById(R.id.world_name);
        final EditText seed = (EditText) findViewById(R.id.seed);

        modeButtons = new Button[]{
                (Button) findViewById(R.id.gm_survival),
                (Button) findViewById(R.id.gm_creative),
                (Button) findViewById(R.id.gm_adventure),
                (Button) findViewById(R.id.gm_hardcore)
        };
        for (int i = 0; i < modeButtons.length; i++) {
            final int mode = i;
            modeButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    gamemode = mode;
                    refreshModes();
                }
            });
        }
        refreshModes();

        ((Button) findViewById(R.id.btn_create)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = worldName.getText().toString().trim();
                if (name.length() == 0) name = "Новый мир";
                String seedText = seed.getText().toString().trim();
                long worldSeed;
                if (seedText.length() == 0) {
                    worldSeed = System.nanoTime();
                } else {
                    try {
                        worldSeed = Long.parseLong(seedText);
                    } catch (NumberFormatException e) {
                        worldSeed = seedText.hashCode() * 1000003L;
                    }
                }
                Intent i = new Intent(NewWorldActivity.this, GameActivity.class);
                i.putExtra(GameActivity.EXTRA_WORLD_NAME, name);
                i.putExtra(GameActivity.EXTRA_SEED, worldSeed);
                i.putExtra(GameActivity.EXTRA_GAMEMODE, gamemode);
                i.putExtra(GameActivity.EXTRA_NICKNAME, nickname);
                i.putExtra(GameActivity.EXTRA_VERSION, version);
                startActivity(i);
            }
        });

        ((Button) findViewById(R.id.btn_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void refreshModes() {
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].setSelected(i == gamemode);
            modeButtons[i].setAlpha(i == gamemode ? 1f : 0.75f);
        }
    }
}
