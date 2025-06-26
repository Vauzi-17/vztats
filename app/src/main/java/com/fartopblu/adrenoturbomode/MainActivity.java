package com.fartopblu.adrenoturbomode;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends Activity {
    static {
        System.loadLibrary("adrenoturboswitch");
    }

    private native void EnableTurbo();
    private native void DisableTurbo();


    private static final String PREFS_NAME = "Preferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    private void showAboutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.about, findViewById(android.R.id.content), false);
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainactivity);

        MaterialButton button_enable = findViewById(R.id.button_enable);
        MaterialButton button_disable = findViewById(R.id.button_disable);
        MaterialButton buttonAbout = findViewById(R.id.button_about);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstRun = preferences.getBoolean(KEY_FIRST_RUN, true);


        if (isFirstRun){
            showAboutDialog();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(KEY_FIRST_RUN, false);
            editor.apply();
        }


        buttonAbout.setOnClickListener((l) -> showAboutDialog());

        button_enable.setOnClickListener((l) -> {
            EnableTurbo();
            button_enable.setEnabled(false);
        });

        button_disable.setOnClickListener((l) -> {
            DisableTurbo();
            button_enable.setEnabled(true);
        });
    }
}