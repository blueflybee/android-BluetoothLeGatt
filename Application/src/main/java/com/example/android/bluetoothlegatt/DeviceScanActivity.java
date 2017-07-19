/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

import static android.os.Build.VERSION.SDK;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */

public class DeviceScanActivity extends ListActivity implements EasyPermissions.PermissionCallbacks {
  private LeDeviceListAdapter mLeDeviceListAdapter;
  private BluetoothAdapter mBluetoothAdapter;
  private boolean mScanning;
  private Handler mHandler;

  private static final String TAG = "DeviceScanActivity";
  private static final int RC_ACCESS_COARSE_LOCATION = 1;

  private static final int REQUEST_ENABLE_BT = 1;
  // Stops scanning after 10 seconds.
  private static final long SCAN_PERIOD = 10000;


  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getActionBar().setTitle(R.string.title_devices);
    mHandler = new Handler();

    // Use this check to determine whether BLE is supported on the device.  Then you can
    // selectively disable BLE-related features.
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
      finish();
    }

    // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
    // BluetoothAdapter through BluetoothManager.
    final BluetoothManager bluetoothManager =
        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    mBluetoothAdapter = bluetoothManager.getAdapter();

    // Checks if Bluetooth is supported on the device.
    if (mBluetoothAdapter == null) {
      Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    String[] perms = {Manifest.permission.ACCESS_COARSE_LOCATION};
    if (!EasyPermissions.hasPermissions(this, perms)) {
      EasyPermissions.requestPermissions(this, "连接蓝牙设备需要扫描蓝牙设备权限", RC_ACCESS_COARSE_LOCATION, perms);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      BluetoothLeAdvertiser bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
      if (bluetoothLeAdvertiser == null) {
        Toast.makeText(this, "the device not support peripheral", Toast.LENGTH_SHORT).show();
        Log.e(TAG, "the device not support peripheral");
        finish();
      }

      bluetoothLeAdvertiser.startAdvertising(BLEPeripheralUtil.createAdvSettings(true, 20000), BLEPeripheralUtil.createAdvertiseData(), mAdvertiseCallback);
    }



  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    if (!mScanning) {
      menu.findItem(R.id.menu_stop).setVisible(false);
      menu.findItem(R.id.menu_scan).setVisible(true);
      menu.findItem(R.id.menu_refresh).setActionView(null);
    } else {
      menu.findItem(R.id.menu_stop).setVisible(true);
      menu.findItem(R.id.menu_scan).setVisible(false);
      menu.findItem(R.id.menu_refresh).setActionView(
          R.layout.actionbar_indeterminate_progress);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_scan:
        mLeDeviceListAdapter.clear();
        scanLeDevice(true);
        break;
      case R.id.menu_stop:
        scanLeDevice(false);
        break;
    }
    return true;
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
    // fire an intent to display a dialog asking the user to grant permission to enable it.
    if (!mBluetoothAdapter.isEnabled()) {
      if (!mBluetoothAdapter.isEnabled()) {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
      }
    }

    // Initializes list view adapter.
    mLeDeviceListAdapter = new LeDeviceListAdapter();
    setListAdapter(mLeDeviceListAdapter);
    scanLeDevice(true);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // User chose not to enable Bluetooth.
    if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
      finish();
      return;
    } else if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
      // Do something after user returned from app settings screen, like showing a Toast.
      Toast.makeText(this, "returned from app settings", Toast.LENGTH_SHORT)
          .show();
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  protected void onPause() {
    super.onPause();
    scanLeDevice(false);
    mLeDeviceListAdapter.clear();
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
    if (device == null) return;
    final Intent intent = new Intent(this, DeviceControlActivity.class);
    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
    if (mScanning) {
      mBluetoothAdapter.stopLeScan(mLeScanCallback);
      mScanning = false;
    }
    startActivity(intent);
  }

  private void scanLeDevice(final boolean enable) {
    if (enable) {
      // Stops scanning after a pre-defined scan period.
      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          mScanning = false;
          mBluetoothAdapter.stopLeScan(mLeScanCallback);
          invalidateOptionsMenu();
        }
      }, SCAN_PERIOD);

      mScanning = true;
      mBluetoothAdapter.startLeScan(mLeScanCallback);
    } else {
      mScanning = false;
      mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }
    invalidateOptionsMenu();
  }

  @Override
  public void onPermissionsGranted(int requestCode, List<String> perms) {
    Toast.makeText(this, "onPermissionsGranted", Toast.LENGTH_SHORT)
        .show();
  }

  @Override
  public void onPermissionsDenied(int requestCode, List<String> perms) {
    Toast.makeText(this, "onPermissionsDenied", Toast.LENGTH_SHORT).show();
    Log.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());

    // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
    // This will display a dialog directing them to enable the permission in app settings.
    if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
      new AppSettingsDialog.Builder(this).build().show();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    // Forward results to EasyPermissions
    EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
  }

  // Adapter for holding devices found through scanning.
  private class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> mLeDevices;
    private LayoutInflater mInflator;

    public LeDeviceListAdapter() {
      super();
      mLeDevices = new ArrayList<BluetoothDevice>();
      mInflator = DeviceScanActivity.this.getLayoutInflater();
    }

    public void addDevice(BluetoothDevice device) {
      if (!mLeDevices.contains(device)) {
        mLeDevices.add(device);
      }
    }

    public BluetoothDevice getDevice(int position) {
      return mLeDevices.get(position);
    }

    public void clear() {
      mLeDevices.clear();
    }

    @Override
    public int getCount() {
      return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
      return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
      return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
      ViewHolder viewHolder;
      // General ListView optimization code.
      if (view == null) {
        view = mInflator.inflate(R.layout.listitem_device, null);
        viewHolder = new ViewHolder();
        viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
        viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
        view.setTag(viewHolder);
      } else {
        viewHolder = (ViewHolder) view.getTag();
      }

      BluetoothDevice device = mLeDevices.get(i);
      final String deviceName = device.getName();
      if (deviceName != null && deviceName.length() > 0)
        viewHolder.deviceName.setText(deviceName);
      else
        viewHolder.deviceName.setText(R.string.unknown_device);
      viewHolder.deviceAddress.setText(device.getAddress());

      return view;
    }
  }

  // Device scan callback.
  private BluetoothAdapter.LeScanCallback mLeScanCallback =
      new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mLeDeviceListAdapter.addDevice(device);
              mLeDeviceListAdapter.notifyDataSetChanged();
            }
          });
        }
      };

  static class ViewHolder {
    TextView deviceName;
    TextView deviceAddress;
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
      super.onStartSuccess(settingsInEffect);
      if (settingsInEffect != null) {
        Log.d(TAG, "onStartSuccess TxPowerLv=" + settingsInEffect.getTxPowerLevel() + " mode=" + settingsInEffect.getMode()
            + " timeout=" + settingsInEffect.getTimeout());
      } else {
        Log.e(TAG, "onStartSuccess, settingInEffect is null");
      }
      Log.e(TAG, "onStartSuccess settingsInEffect" + settingsInEffect);

    }

    @Override
    public void onStartFailure(int errorCode) {
      super.onStartFailure(errorCode);
      Log.e(TAG, "onStartFailure errorCode" + errorCode);

      if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
//        Toast.makeText(this, R.string.advertise_failed_data_too_large, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes.");
      } else if (errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
//        Toast.makeText(this, R.string.advertise_failed_too_many_advertises, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Failed to start advertising because no advertising instance is available.");
      } else if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
//        Toast.makeText(this, R.string.advertise_failed_already_started, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Failed to start advertising as the advertising is already started");
      } else if (errorCode == ADVERTISE_FAILED_INTERNAL_ERROR) {
//        Toast.makeText(this, R.string.advertise_failed_internal_error, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Operation failed due to an internal error");
      } else if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
//        Toast.makeText(this, R.string.advertise_failed_feature_unsupported, Toast.LENGTH_LONG).show();
        Log.e(TAG, "This feature is not supported on this platform");
      }
    }
  };
}