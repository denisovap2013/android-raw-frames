package com.rawframesrecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CheckAndRequestPermissions();
    }

    private boolean isPermissionGranted(String permissionName) {
        return checkSelfPermission(permissionName) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean requestSinglePermission(String permissionName) {
        if (isPermissionGranted(permissionName)) return true;
        requestPermissions(new String[]{permissionName}, 1);
        return isPermissionGranted(permissionName);
    }

    private boolean CheckAndRequestPermissions() {
        return (requestSinglePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                && requestSinglePermission(Manifest.permission.CAMERA));
    }
}