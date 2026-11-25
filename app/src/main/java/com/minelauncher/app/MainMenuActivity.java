package com.minelauncher.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Minecraft-style main menu ("Title screen").
 */
public class MainMenuActivity extends Activity {

    private String nickname = "Steve";
    private String version = "1.21.4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        Intent in = getIntent();
        if (in != null) {
            String n = in.getStringExtra("nickname");
            if (n != null && n.length() > 0) nickname = n;
            String v = in.getStringExtra("version");
            if (v != null && v.length() > 0) version = v;
        }

        TextView subtitle = (TextView) findViewById(R.id.menu_subtitle);
        subtitle.setText(nickname + " · Java Edition " + version);

        TextView footer = (TextView) findViewById(R.id.footer);
        footer.setText("Mine Launcher 1.0 · " + version + " · " + nickname);

        findViewById(R.id.btn_singleplayer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainMenuActivity.this, NewWorldActivity.class);
                i.putExtra("nickname", nickname);
                i.putExtra("version", version);
                startActivity(i);
            }
        });

        ((Button) findViewById(R.id.btn_settings)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainMenuActivity.this, SettingsActivity.class));
            }
        });

        ((Button) findViewById(R.id.btn_exit)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
