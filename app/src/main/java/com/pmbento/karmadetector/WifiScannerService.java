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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import java.util.List;
import java.util.Random;

//import android.util.Log;


public class WifiScannerService extends IntentService {
    private int frequency; // scan frequency (in seconds)
    private BroadcastReceiver receiver;
    private WifiManager wifiManager;
    private WifiManager.WifiLock wifiLock;
    private String decoySsid;
    private boolean shouldRun = true;
    private Uri defaultNotificationUri;
    private NotificationManager notificationManager;

    public WifiScannerService() {
        super(WifiScannerService.class.getName());
    }

    private static String getRandomString(int length) {
        final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random rnd = new Random();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        SharedPreferences sharedPreferences = getSharedPreferences("karmaDetectorPrefs", Context.MODE_PRIVATE);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "WifiScannerService");
        defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        shouldRun = true;
        int DEFAULT_SCAN_FREQ = 300;
        frequency = sharedPreferences.getInt("scanFrequency", DEFAULT_SCAN_FREQ);

        if (!wifiManager.isWifiEnabled()) {
            boolean ret = wifiManager.setWifiEnabled(true);
            if (!ret)
                addToLog("Problem activating Wifi. Active scans will not work.");
        }
        removeDecoyNetworks();
        createDecoyNetwork();

        startBroadcastReceiver();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        //Log.d("WifiScannerService", "Going to destroy the handler");
        shouldRun = false;
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        releaseWifiLock();
        removeDecoyNetworks();
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent workIntent) {
        //Log.d("WifiScannerService", "Handling");
        startNetworkScanner();
        //Log.d("WifiScannerService", "Done Handling");
    }

    private void startBroadcastReceiver() {
        IntentFilter i = new IntentFilter();
        i.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                if (!i.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    //Log.d("WifiScannerService", "Action received but not handled: " + i.getAction());
                    return;
                }
                List<ScanResult> l = wifiManager.getScanResults();
                for (ScanResult r : l) {
                    //Log.d("WifiScannerService", r.SSID);
                    if (r.SSID.equals(decoySsid)) {

                        sendNotification("BSSID: " + r.BSSID + "\n" + "Signal level: " + r.level);
                        String message;
                        message = "Possible Karma attack detected!" + "\n"
                                + "BSSID: " + r.BSSID + "\n"
                                + "Decoy SSID: " + r.SSID + "\n"
                                + "Capabilities: " + r.capabilities + "\n"
                                + "Frequency: " + r.frequency + "\n"
                                + "Signal level (in dBm): " + r.level;
                        //Log.d("WifiScannerService", message);
                        addToLog(message);
                    }
                }
                releaseWifiLock();
            }
        };
        registerReceiver(receiver, i);
    }

    private boolean createDecoyNetwork() {
        decoySsid = "KD-" + getRandomString(13);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = decoySsid;
        wifiConfiguration.preSharedKey = "\"".concat(getRandomString(63)).concat("\"");
        wifiConfiguration.hiddenSSID = true;
        wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
        wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

        //Log.d("WifiPreference", "going to add network " + decoySsid);
        int networkId = wifiManager.addNetwork(wifiConfiguration);
        if (networkId == -1) {
            addToLog("Error adding decoy network!");
            return false;
        }
        //Log.d("WifiPreference", "add Network returned " + networkId);
        boolean es = wifiManager.saveConfiguration();
        //Log.d("WifiPreference", "saveConfiguration returned " + es);
        if (!es) {
            addToLog("Error saving the network configuration for the decoy network!");
            return false;
        }
        boolean b = wifiManager.enableNetwork(networkId, false);
        //Log.d("WifiPreference", "enableNetwork returned " + b);
        if (!b) {
            addToLog("Error enabling the decoy network!");
            return false;
        }
        return true;
    }

    private boolean removeDecoyNetworks() {
        //Log.d("WifiScannerService", "Clearing old decoy networks");
        List<WifiConfiguration> l = wifiManager.getConfiguredNetworks();
        if (l != null) {
            for (WifiConfiguration r : l) {
                if (r.SSID.startsWith("\"KD-") && r.SSID.length() == 18) {
                    //Log.d("WifiScannerService", "Going to remove network: " + r.SSID);
                    boolean ea = wifiManager.removeNetwork(r.networkId);
                    //Log.d("WifiPreference", "remove Network returned " + ea);
                    if (!ea) {
                        addToLog("Error removing decoy network!");
                        return false;
                    }
                }
            }
            boolean es = wifiManager.saveConfiguration();
            //Log.d("WifiPreference", "saveConfiguration returned " + es);
            if (!es) {
                addToLog("Error saving the network configuration while removing decoy networks!");
                return false;
            }
        }
        return true;
    }

    private void startNetworkScanner() {
        while (shouldRun) {
            //Log.d("WifiScannerService", "Starting scan. Next scan in " + frequency + " seconds.");
            addToLog("Starting scan. Next scan in " + frequency + " seconds.");
            acquireWifiLock();
            wifiManager.startScan();
            try {
                Thread.sleep(frequency * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendNotification(String contentText) {
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_kd)
                .setContentTitle("Possible Karma attack detected!")
                .setContentText(contentText)
                .setSound(defaultNotificationUri)
                .setLights(Color.RED, 500, 500)
                .setOnlyAlertOnce(true)
                .setContentIntent(intent)
                .setAutoCancel(true).build();

        notificationManager.notify(0, n);
    }

    private void addToLog(String message) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MainActivity.ResponseReceiver.ACTION_RESP);
        broadcastIntent.putExtra("message", message);
        sendBroadcast(broadcastIntent);
    }

    private void acquireWifiLock() {
        if (!wifiLock.isHeld()) {
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
            //Log.d("WifiScannerService", "wifi lock acquired: " + wifiLock.isHeld());
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null) {
            if (wifiLock.isHeld()) {
                wifiLock.release();
            }
        }
    }

    public static class BroadcastResponseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                SharedPreferences sharedPreferences = context.getSharedPreferences("karmaDetectorPrefs", Context.MODE_PRIVATE);
                if (sharedPreferences.getBoolean("autoStart", false)) {
                    Intent wifiScannerIntent = new Intent(context, WifiScannerService.class);
                    context.startService(wifiScannerIntent);
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(MainActivity.ResponseReceiver.ACTION_RESP);
                    broadcastIntent.putExtra("message", "Current state: running");
                    context.sendBroadcast(broadcastIntent);
                }
            }
        }
    }
}
