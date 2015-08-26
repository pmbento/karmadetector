/**
 * Copyright (C) 2015 Patrício Batista
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.pmbento.karmadetector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private final ResponseReceiver receiver = new ResponseReceiver();
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private final CompoundButton.OnCheckedChangeListener autoStartSwitchWatcher = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            editor = sharedPreferences.edit();
            editor.putBoolean("autoStart", isChecked);
            editor.apply();
        }
    };
    private Intent wifiScannerIntent;
    private TextView textViewLog;
    private EditText scanFrequencyText;
    private int scanFrequency;
    private final TextWatcher frequencyScanTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        public void afterTextChanged(Editable s) {
            try {
                scanFrequency = Integer.parseInt(s.toString());
                if (scanFrequency < 5)
                    scanFrequency = 5;
            } catch (Exception e) {
                scanFrequency = 5;
            }

            editor = sharedPreferences.edit();
            editor.putInt("scanFrequency", scanFrequency);
            editor.apply();
        }
    };
    private Switch autoStartSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textViewLog = (TextView) findViewById(R.id.textViewLog);
        wifiScannerIntent = new Intent(this, WifiScannerService.class);
        textViewLog.setMovementMethod(new ScrollingMovementMethod());
        sharedPreferences = getSharedPreferences("karmaDetectorPrefs", Context.MODE_PRIVATE);
        scanFrequencyText = (EditText) findViewById(R.id.scanFrequency);
        scanFrequencyText.addTextChangedListener(frequencyScanTextWatcher);
        autoStartSwitch = (Switch) findViewById(R.id.switchAutoStart);
        autoStartSwitch.setOnCheckedChangeListener(autoStartSwitchWatcher);
        loadPrefs();
        addToLog("App started.");
    }

    @Override
    protected void onDestroy() {
        stopService(wifiScannerIntent);
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPrefs();
        registerReceiver(receiver, new IntentFilter(ResponseReceiver.ACTION_RESP));
    }

    public void startScanner(View v) {
        startService(wifiScannerIntent);
        addToLog("Requested scan service start.");
    }

    public void stopScanner(View v) {
        stopService(wifiScannerIntent);
        addToLog("Requested scan service stop.");
    }

    private void addToLog(String content) {
        final int LOG_MAX_BUFFER = 2000;
        // lame log rotation implementation
        if (textViewLog.length() > LOG_MAX_BUFFER)
            textViewLog.setText(textViewLog.getText().toString().substring(100, textViewLog.length()));
        String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
        textViewLog.append(currentDateTimeString + ": " + content + "\n");
    }

    private void loadPrefs() {
        autoStartSwitch.setChecked(sharedPreferences.getBoolean("autoStart", false));
        scanFrequency = sharedPreferences.getInt("scanFrequency", 0);
        if (scanFrequency == 0) {
            editor = sharedPreferences.edit();
            int DEFAULT_SCAN_FREQ = 300;
            editor.putInt("scanFrequency", DEFAULT_SCAN_FREQ);
            editor.apply();
            scanFrequencyText.setText("" + DEFAULT_SCAN_FREQ);
        } else {
            scanFrequencyText.setText("" + scanFrequency);
        }
    }

    public class ResponseReceiver extends BroadcastReceiver {
        public static final String ACTION_RESP = "com.pmbento.karmadetector.wifiscannerservice.response";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_RESP)) {
                addToLog(intent.getStringExtra("message"));
            }
        }
    }
}
