package com.fartopblu.adrenoturbomode;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.DynamicColors;
import android.widget.TextView;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.TimerTask;
import java.util.Timer;

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

        // Frequency check
        boolean gpu_freq_support = true;
        try (Scanner scanner = new Scanner(new File("/sys/class/kgsl/kgsl-3d0/gpuclk"))) {
        } catch (FileNotFoundException e) {
            gpu_freq_support = false;
            TextView gpu_freq_text = findViewById(R.id.textGpuFreq);
            gpu_freq_text.setText("Current frequency: Error, displaying unsupported on this device.");
        }

        if (gpu_freq_support) {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try (Scanner scanner = new Scanner(new File("/sys/class/kgsl/kgsl-3d0/gpuclk"))) {
                        int freq = (scanner.nextInt())/1000000;
                        runOnUiThread(() -> {
                            TextView gpu_freq_text = findViewById(R.id.textGpuFreq);
                            gpu_freq_text.setText("Current frequency: " + freq + " Mhz");
                        });
                    } catch (FileNotFoundException ignored) {
                    }
                }
            }, 0, 1300);
        }
    }
}