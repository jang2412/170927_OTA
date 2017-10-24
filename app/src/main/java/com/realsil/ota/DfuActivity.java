package com.realsil.ota;

/*
 * Copyright (C) 2015 Realsil Corporation
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.realsil.android.blehub.dfu.BinInputStream;
import com.realsil.android.blehub.dfu.RealsilDfu;
import com.realsil.android.blehub.dfu.RealsilDfuCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

public class DfuActivity extends Activity implements LoaderCallbacks<Cursor>, ScannerFragment.OnDeviceSelectedListener {
    private static final String TAG = "DfuActivity";
    private static final String DATA_FILE_PATH = "file_path";
    private static final String DATA_FILE_STREAM = "file_stream";
    private static final String DATA_STATUS = "status";
    private static final String EXTRA_URI = "uri";
    private static final boolean D = true;
    private boolean mUpdateSuccessful = false;
    private boolean mStatusOk;

    private static final int SELECT_FILE_REQ = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    // Handle msg
    private static final int OTA_FIND_CHARAC_SUCCESS = 0;
    private static final int OTA_GATT_CONNECTIION_FAIL = 1;
    private static final int OTA_GATT_DISCOVERY_FAIL = 2;
    private static final int OTA_GET_SERVICE_FAIL = 3;
    private static final int OTA_GET_CHARA_FAIL = 4;
    private static final int OTA_START_OTA_PROCESS = 5;
    private static final int OTA_GET_TARGET_APP_VERSION = 6;
    private static final int OTA_GET_TARGET_PATCH_VERSION = 7;
    private static final int OTA_GET_FILE_INFO_SUCCESS = 8;
    private static final int OTA_GET_FILE_INFO_FAIL = 9;
    private static final int OTA_CALLBACK_STATE_CHANGE = 10;
    private static final int OTA_CALLBACK_PROCESS_CHANGE = 11;
    private static final int OTA_CALLBACK_SUCCESS = 12;
    private static final int OTA_CALLBACK_ERROR = 13;
    private static final int OTA_GET_DFU_SERVICE = 14;

    private String mDeviceName = null;
    private String mDeviceAddress;
    private TextView mDeviceNameView;
    private TextView mFileNameView;
    private TextView mFileSizeView;
    private TextView mFileVersionView;
    private TextView mTargetVersionView;
    private TextView mPatchVersionView;
    private TextView mFileStatusView;
    private TextView mTextPercentage;
    private TextView mTextUploading;
    private ProgressBar mProgressBar;
    private Button mSelectFileButton;
    private Button mUploadButton;
    private Button mSelectTargetButton;

    private String mFilePath;
    private BinInputStream mBinInputStream;
    private int newFwVersion;
    private int oldFwVersion;
    private int newPatchVersion;
    private int oldPatchVersion;
    private Uri mFileStreamUri;

    private String a=null;
    private String b=null;

    //private final UUID[] serviceUuids = {UUID.fromString("00006287-3c17-d293-8e48-14fe2e4da212")};
    //private final UUID[] serviceUuids = {UUID.fromString("0x6287")};
    private final static UUID OTA_SERVICE_UUID = UUID.fromString("0000ffd0-0000-1000-8000-00805f9b34fb");
    //Modify for new spec
    private static final UUID NEW_OTA_SERVICE_UUID= UUID.fromString("0000d0ff-3c17-d293-8e48-14fe2e4da212");

    private final static UUID OTA_CHARACTERISTIC_UUID = UUID.fromString("0000ffd1-0000-1000-8000-00805f9b34fb");
    private final static UUID OTA_READ_PATCH_CHARACTERISTIC_UUID = UUID.fromString("0000ffd3-0000-1000-8000-00805f9b34fb");
    private final static UUID OTA_READ_APP_CHARACTERISTIC_UUID = UUID.fromString("0000ffd4-0000-1000-8000-00805f9b34fb");
    private final static UUID DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private static final UUID Software_revision_UUID = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");
    private final static UUID PNP_ID_CHARACTERISTIC_UUID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb");



    /*
     * DFU Service UUID
     */
    public static final UUID DFU_SERVICE_UUID = UUID.fromString("00006287-3c17-d293-8e48-14fe2e4da212");
    private BluetoothGattCharacteristic mReadAppCharacteristic, mReadPatchCharacteristic;

    // dfu object
    private RealsilDfu dfu = null;

    private BluetoothAdapter mBtAdapter;
    private BluetoothGatt mBtGatt;
    private BluetoothDevice mSelectedDevice;
    private BluetoothGattCharacteristic m_SoftwareRevision;

    private GlobalGatt mGlobalGatt;

    private ProgressDialog mProgressDialog = null;

    private Handler mHandle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            if(D) Log.d(TAG, "MSG No " + msg.what);
            switch (msg.what) {
                case OTA_FIND_CHARAC_SUCCESS:
                    // Add Progress bar.
                    mProgressDialog.cancel();
                    showToast("gatt connected");
                    mSelectFileButton.setEnabled(true);
                    mSelectFileButton.requestFocus();
                    // set the dfu service work mode
                    if(dfu.setWorkMode(RealsilDfu.OTA_MODE_FULL_FUNCTION) != true) {
                        showToast("Something error in set OTA work mode");
                        mUploadButton.setEnabled(false);
                        mSelectFileButton.setEnabled(false);
                        mGlobalGatt.disconnectGatt(mDeviceAddress);
                    }
                    onSelectFile();
                    break;
                case OTA_GET_TARGET_PATCH_VERSION:
                    mPatchVersionView.setText(String.valueOf(msg.arg1));
                    Log.d("ldsdebug patch", String.valueOf(msg.arg1));
                    break;
                case OTA_GET_TARGET_APP_VERSION:
                    mTargetVersionView.setText(String.valueOf(msg.arg1));
                    Log.d("ldsdebug app", String.valueOf(msg.arg1));
                    break;
                case OTA_GET_DFU_SERVICE:
                    // Add Progress bar.
                    mProgressDialog.cancel();
                    // in dfu mode can not get version info
                    mPatchVersionView.setText("Dfu Service unsupport");
                    mTargetVersionView.setText("Dfu Service unsupport");
                    showToast("Dfu gatt connected");
                    mSelectFileButton.setEnabled(true);
                    mSelectFileButton.requestFocus();
                    // set the dfu service work mode
                    if(dfu.setWorkMode(RealsilDfu.OTA_MODE_LIMIT_FUNCTION) != true) {
                        showToast("Something error in set OTA work mode");
                        mUploadButton.setEnabled(false);
                        mSelectFileButton.setEnabled(false);
                        mGlobalGatt.disconnectGatt(mDeviceAddress);
                    }
                    break;
                case OTA_GET_FILE_INFO_SUCCESS:
                    //final File file = new File(mFilePath);
                    b = mSelectedDevice.getName();
                    Log.d("Device Name ---- ", b);
                    if(b.equals("ADVRF-RCUM")){
                        Log.d("Device Name ---- ", b);
                        mFilePath = "/storage/emulated/0/Download/ADVRF_KEY_MATRIX_V00.05.00_BUILD_2017082301/APP_1.0.0-841b28e6e19b31485d4b5d03ef68b1ae.bin";
                    }
                    if(b.equals("AURORA-BLE-RCU")){
                        Log.d("Device Name ---- ", b);
                        mFilePath = "/storage/emulated/0/Download/Release Version 7.0.7 20170906/APP_1.0.7-71d3b69f91f5202f9e42b50b1e570caa.bin";
                    }
                    final File file = new File(mFilePath);
                    //file = new File("/storage/E867-15F2/003_aurora/170906_aurora_7.0.7/Release Version 7.0.7 20170906/APP_1.0.7-71d3b69f91f5202f9e42b50b1e570caa.bin");
                    mFileVersionView.setText(String.valueOf(newFwVersion));
                    mFileNameView.setText(file.getName());
                    mFileSizeView.setText(getString(R.string.dfu_file_size_text, file.length()));
                    mFileStatusView.setText(R.string.dfu_file_status_ok);
                    mUploadButton.setEnabled(true);
                    mUploadButton.requestFocus();
                    break;
                case OTA_GET_FILE_INFO_FAIL:
                    mFileNameView.setText("");
                    mFileVersionView.setText("");
                    mFileSizeView.setText("");
                    mFileStatusView.setText(R.string.dfu_file_status_invalid);
                    mUploadButton.setEnabled(false);
                    break;
                case OTA_CALLBACK_PROCESS_CHANGE:
                    mProgressBar.setProgress(msg.arg1);
                    mTextPercentage.setText(getString(R.string.progress, msg.arg1));
                    break;
                case OTA_CALLBACK_STATE_CHANGE:
                    switch (msg.arg1) {
                        case RealsilDfu.STA_ORIGIN:
                            mTextUploading.setText("STA_ORIGIN");
                            break;
                        case RealsilDfu.STA_REMOTE_ENTER_OTA:
                            mTextUploading.setText("STA_REMOTE_ENTER_OTA");
                            break;
                        case RealsilDfu.STA_FIND_OTA_REMOTE:
                            mTextUploading.setText("STA_FIND_OTA_REMOTE");
                            break;
                        case RealsilDfu.STA_CONNECT_OTA_REMOTE:
                            mTextUploading.setText("STA_CONNECT_OTA_REMOTE");
                            break;
                        case RealsilDfu.STA_START_OTA_PROCESS:
                            mTextUploading.setText("STA_START_OTA_PROCESS");
                            break;
                        case RealsilDfu.STA_OTA_UPGRADE_SUCCESS:
                            mTextUploading.setText("STA_OTA_UPGRADE_SUCCESS");
                            break;
                        default:
                            break;
                    }
                    break;
                case OTA_CALLBACK_SUCCESS:
                    mUpdateSuccessful = true;
                    showToast("Congratulations! Update Success! success code: " + msg.arg1);
                    clearUI();
                    break;
                case OTA_CALLBACK_ERROR:
                    mUpdateSuccessful = false;
                    showToast("Upload failed, error code: " + msg.arg1);
                    clearUI();
                    break;
                case OTA_GATT_CONNECTIION_FAIL:
                case OTA_GATT_DISCOVERY_FAIL:
                case OTA_GET_CHARA_FAIL:
                case OTA_GET_SERVICE_FAIL:
                    mProgressDialog.cancel();
                    showToast("Something error in get remote info, error code: " + msg.what);
                    mUploadButton.setEnabled(false);
                    mSelectFileButton.setEnabled(false);
                    break;
                default:
                    break;
            }

            super.handleMessage(msg);
        }

    };

    private Handler nHandle = new Handler();
    private Runnable ver = new Runnable() {
        @Override
        public void run() {
            if(b.equals("ADVRF-RCUM")){
                    if(a == null){
                        nHandle.postDelayed(ver, 1000);
                    }
                    else{
                        String[] c = a.split("\\.");
                        Log.d("jjydebug","FwVersion ---- " +Arrays.toString(c));
                        if(Integer.parseInt(c[1]) <= 5){
                            Log.d("jjydeb1", c[1]);
                            showVersionCheckDialog();
                            showToast(a);
                        }
                        else {
                            showToast(a);
                        }
                    }
            }
            else if(b.equals("AURORA-BLE-RCU")){
                if(a == null){
                    nHandle.postDelayed(ver, 1000);
                }
                else{
                    String[] c = a.split("\\.");
                    Log.d("jjydebug","FwVersion ---- " +Arrays.toString(c));
                    if(Integer.parseInt(c[0]) <= 7){
                        Log.d("jjydeb1", c[0]);
                        showVersionCheckDialog();
                        showToast(a);
                    }
                    else {
                        showToast(a);
                    }
                }
            }
        }
    };

    /*
    private Handler sHandle = new Handler();
    private Runnable Scandevice = new Runnable() {
        @Override
        public void run() {
            if(mDeviceName == null){
                sHandle.postDelayed(Scandevice, 1000);
            }
            else if(mDeviceName == "AURORA-BLE-RCU"){
                final File file = new File("/storage/E867-15F2/003_aurora/170906_aurora_7.0.7/Release Version 7.0.7 20170906/APP_1.0.7-71d3b69f91f5202f9e42b50b1e570caa.bin");
                Log.d("device ----", "AURORA");
            }
            else if(mDeviceName == "ADVRF-RCUM"){
                final File file = new File("/storage/E867-15F2/003_aurora/170906_aurora_7.0.7/Release Version 7.0.7 20170906/APP_1.0.7-71d3b69f91f5202f9e42b50b1e570caa.bin");
                Log.d("device ----", "ADVRF");
            }
        }
    };
    */

    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            // TODO Auto-generated method stub
            if(D) Log.d(TAG, "onConnectionStateChange: status = " + status + ",newState = " + newState);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                if(newState == BluetoothProfile.STATE_CONNECTED) {
                    if(D) Log.d(TAG, "device connected");
                    if(D) Log.d(TAG, "gatt = " + gatt);
                    if(gatt == null) {
                        if(D) Log.e(TAG, "gatt is null");
                    }
                    else {
                        //Log.d(TAG, "Start to Refresh Services");
                        //refreshDeviceCache(gatt);
                        if(D) Log.d(TAG, "Start to Discovery");
                        gatt.discoverServices();
                    }
                }
                else if((newState == BluetoothProfile.STATE_DISCONNECTED)) {
                    if(D) Log.e(TAG, "disconnect, try to close gatt");
                    mGlobalGatt.close(mDeviceAddress);
                    mBtGatt = null;
                }

            } else {
                mHandle.sendMessage(mHandle.obtainMessage(OTA_GATT_CONNECTIION_FAIL));
                mGlobalGatt.close(mDeviceAddress);
                mBtGatt = null;
            }
        }

        public void getSoftwareRevision(BluetoothGatt gatt) {
            BluetoothGattService softwareService = gatt.getService(DEVICE_INFO_SERVICE_UUID);
            if (softwareService == null) {
                Log.d(TAG, "software service not found!");
                return;
            }

            m_SoftwareRevision = softwareService.getCharacteristic(Software_revision_UUID);
            if (m_SoftwareRevision == null) {
                Log.d(TAG, "Battery level not found!");
                return;
            }
            gatt.readCharacteristic(m_SoftwareRevision);
            Log.v(TAG, "batteryLevel = " + gatt.readCharacteristic(m_SoftwareRevision));
        }

        public void onReadSoftwareRevision() {
            if(m_SoftwareRevision!= null){
                byte nBuff[];
                nBuff = m_SoftwareRevision.getValue();
                Log.d("ldsdebug", "data = " + Arrays.toString(m_SoftwareRevision.getValue()));
                ByteBuffer wrapped = ByteBuffer.wrap(nBuff);
                wrapped.order(ByteOrder.LITTLE_ENDIAN);
                //a = new String(Arrays.toString(m_SoftwareRevision.getValue()));
                //b = a.charAt(9);
                a = m_SoftwareRevision.getStringValue(0);
                Log.d("jjydebug", a);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // TODO Auto-generated method stub
            if(D) Log.d(TAG, "onServicesDiscovered: status = " + status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService mService = gatt.getService(OTA_SERVICE_UUID);
                if(mService == null) {
                    mService = gatt.getService(NEW_OTA_SERVICE_UUID);
                    if(mService == null) {
                        Log.e(TAG, "OTA service not found");
                        //mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_SERVICE_FAIL));
                        // Try to find the DFU service, may devices is already in OTA mode
                        mService = gatt.getService(DFU_SERVICE_UUID);
                        if (mService == null) {
                            mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_SERVICE_FAIL));
                        } else {
                            if(D) Log.d(TAG, "DFU service = " + mService.getUuid());
                            mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_DFU_SERVICE));
                        }
                        return;
                    }
                }else {
                    if(D) Log.d(TAG, "OTA service = " + mService.getUuid());
                }

                getSoftwareRevision(gatt);

                mReadAppCharacteristic = mService.getCharacteristic(OTA_READ_APP_CHARACTERISTIC_UUID);
                if(mReadAppCharacteristic == null) {
                    if(D) Log.e(TAG, "OTA read app characteristic not found");
                    mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_CHARA_FAIL));
                    return;
                }else {
                    if(D) Log.d(TAG, "mEnterOTACharacteristic = " + mReadAppCharacteristic.getUuid());
                    readDeviceInfo(mReadAppCharacteristic);
                }

                mReadPatchCharacteristic = mService.getCharacteristic(OTA_READ_PATCH_CHARACTERISTIC_UUID);
                if(mReadPatchCharacteristic == null) {
                    if(D) Log.e(TAG, "OTA read patch version characteristic not found");
                    mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_CHARA_FAIL));
                    return;
                }else {
                    if(D) Log.d(TAG, "mReadPatchCharacteristic = " + mReadPatchCharacteristic.getUuid());
                }
                mHandle.sendMessage(mHandle.obtainMessage(OTA_FIND_CHARAC_SUCCESS));
            }else{
                if(D) Log.e(TAG, "service discovery failed !!!");
                mHandle.sendMessage(mHandle.obtainMessage(OTA_GATT_DISCOVERY_FAIL));
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            if (status == BluetoothGatt.GATT_SUCCESS){
                if(D) Log.d(TAG, "data = " + Arrays.toString(characteristic.getValue()));
                if(characteristic.getUuid().equals(OTA_READ_APP_CHARACTERISTIC_UUID)) {
                    byte[] appVersionValue = characteristic.getValue();
                    ByteBuffer wrapped = ByteBuffer.wrap(appVersionValue);
                    wrapped.order(ByteOrder.LITTLE_ENDIAN);
                    oldFwVersion = wrapped.getShort(0);
                    //Log.d("ldsdebug", "data = " + Arrays.toString(characteristic.getValue()));
                    Message msg = new Message();
                    msg.what = OTA_GET_TARGET_APP_VERSION;
                    msg.arg1 = oldFwVersion;
                    mHandle.sendMessage(msg);
                    //mTargetVersionView.setText(String.valueOf(oldFwVersion));
                    if(D) Log.d(TAG, "old firmware version: " + oldFwVersion + " .getValue=" + Arrays.toString(characteristic.getValue()));
                    if(mReadPatchCharacteristic != null) {
                        readDeviceInfo(mReadPatchCharacteristic);
                    }
                }else if(characteristic.getUuid().equals(OTA_READ_PATCH_CHARACTERISTIC_UUID)){
                    byte[] patchVersionValue = characteristic.getValue();
                    ByteBuffer wrapped = ByteBuffer.wrap(patchVersionValue);
                    wrapped.order(ByteOrder.LITTLE_ENDIAN);
                    oldPatchVersion = wrapped.getShort(0);
                    //int oldPatchVersion = (characteristic.getValue()[1] & 0xff) *256 + (characteristic.getValue()[0] & 0xff); //This method can also get oldPatchVersion.
                    Message msg = new Message();
                    msg.what = OTA_GET_TARGET_PATCH_VERSION;
                    msg.arg1 = oldPatchVersion;
                    mHandle.sendMessage(msg);
                    if(D) Log.d(TAG, "old patch version: " + oldPatchVersion + " .getValue=" + Arrays.toString(characteristic.getValue()));
                    //here can add read other characteristic
                }
                else if(characteristic.getUuid().equals(Software_revision_UUID)){
                    onReadSoftwareRevision();
                    nHandle.post(ver);
                }
            }
        }
    };

    private void readDeviceInfo(BluetoothGattCharacteristic characteristic) {
        if(D) Log.d(TAG, "read readDeviceinfo:" + characteristic.getUuid().toString());
        if(characteristic != null){
            mBtGatt.readCharacteristic(characteristic);
        } else {
            if(D) Log.e(TAG, "readDeviceinfo Characteristic is null");
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if(D) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feature_dfu);
        // get the Realsil Dfu proxy
        RealsilDfu.getDfuProxy(this, cb);
        // Check whether the ble support or not
        isBLESupported();
        // request to enable BT
        if (!isBLEEnabled()) {
            showBLEDialog();
        }
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
//        final BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
//        mBtAdapter = manager.getAdapter();
        if(mBtAdapter == null) {
            if(D) Log.e(TAG, "Bluetooth Not Suppoerted !!!");
            this.finish();
        }
        // set and initial the GUI
        setGUI();
        clearUI();

        showSelectDevicesDialog();


        mGlobalGatt = GlobalGatt.getInstance();
        mGlobalGatt.initialize();
    }

    @Override
    protected void onResume() {
        if(D) Log.d(TAG, "onResume");
        mSelectTargetButton.requestFocus();
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
//        if (!mBtAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
    }

    @Override
    protected void onStart()
    {
        if(D) Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onPause() {
        if(D) Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        if(D) Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onRestart(){
        if(D) Log.d(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        if(D) Log.d(TAG, "onDestroy");
        super.onDestroy();
        if(dfu != null) {
            dfu.close();
        }
        // disconnect and close the gatt
        mGlobalGatt.closeAll();

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            AlertDialog.Builder aa = new AlertDialog.Builder(this);
            aa.setTitle("Toast message");
            aa.setMessage("Are you sure to exit?");
            aa.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    DfuActivity.this.finish();
                }
            });
            aa.setNegativeButton("No", null);
            aa.create();
            aa.show();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showSelectDevicesDialog(){
        final FragmentManager fm = getFragmentManager();
        // start le scan, with no filter
        final ScannerFragment dialog = ScannerFragment.getInstance(DfuActivity.this, OTA_SERVICE_UUID, false);
        dialog.show(fm, "scan_fragment");
    }

    // implement the scanner fragment method
    @Override
    public void onDeviceSelected(final BluetoothDevice device) {
        mSelectedDevice = device;

        mDeviceName = mSelectedDevice.getName();
        mDeviceAddress = mSelectedDevice.getAddress();

        mProgressDialog = ProgressDialog.show(DfuActivity.this
                , null
                , "Connect to the device: " + mDeviceAddress + ", please wait..."
                , true);
        mProgressDialog.setCancelable(false);
        // use GlobalGatt
        //mBtGatt = mSelectedDevice.connectGatt(getApplicationContext(), false, new VoiceOverHogpCallback());
        mGlobalGatt.connect(mDeviceAddress, mBluetoothGattCallback);
        mBtGatt = mGlobalGatt.getBluetoothGatt(mDeviceAddress);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DATA_FILE_PATH, mFilePath);
        outState.putParcelable(DATA_FILE_STREAM, mFileStreamUri);
        outState.putBoolean(DATA_STATUS, mStatusOk);
    }

    private void setGUI() {
        final ActionBar actionBar = getActionBar();
        //actionBar.setHomeButtonEnabled(false);
        //mDeviceNameView = (TextView) findViewById(R.id.device_name);
        //mDeviceNameView.setText(mDeviceName);
        mFileNameView       = (TextView)findViewById(R.id.file_name);
        mFileSizeView       = (TextView)findViewById(R.id.file_size);
        mFileVersionView    = (TextView)findViewById(R.id.newFwVersionTextView);
        mTargetVersionView  = (TextView)findViewById(R.id.oldFwVersionTextView);
        mPatchVersionView   = (TextView)findViewById(R.id.oldPatchVersionTextView);
        mFileStatusView     = (TextView)findViewById(R.id.file_status);
        //mSelectFileButton   = (Button)findViewById(R.id.action_select_file);
        mSelectFileButton   = (Button)findViewById(R.id.action_select_file);
        mSelectTargetButton = (Button)findViewById(R.id.select_target_button);
        mUploadButton        = (Button) findViewById(R.id.action_upload);
        mTextPercentage     = (TextView)findViewById(R.id.textviewProgress);
        mTextUploading      = (TextView)findViewById(R.id.textviewUploading);
        mProgressBar         = (ProgressBar)findViewById(R.id.progressbar_file);
    }

    private void isBLESupported() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showToast(R.string.no_ble);
            finish();
        }
    }

    private void showToast(final int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private boolean isBLEEnabled() {
        final BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = manager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private void showBLEDialog() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dfu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                final AppHelpFragment fragment = AppHelpFragment.getInstance(R.string.dfu_about_text);
                fragment.show(getFragmentManager(), "help_fragment");
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case SELECT_FILE_REQ:
                if (resultCode != RESULT_OK)
                    return;

                // clear previous data
                mFilePath = null;
                mFileStreamUri = null;


                // and read new one
                final Uri uri = data.getData();
            /*
             * The URI returned from application may be in 'file' or 'content' schema.
             * 'File' schema allows us to create a File object and read details from if directly.
             *
             * Data from 'Content' schema must be read by Content Provider. To do that we are using a Loader.
             */
                if (uri.getScheme().equals("file")) {
                    // the direct path to the file has been returned
                    final String path = uri.getPath();
                    // load the file
                    if(LoadFileInfo(path) == true) {
                        // send msg
                        mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_SUCCESS));
                    } else {
                        showToast("something error in load file");
                        // send msg
                        mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_FAIL));
                    }

                } else if (uri.getScheme().equals("content")) {
                    // an Uri has been returned
                    mFileStreamUri = uri;
                    // if application returned Uri for streaming, let's us it. Does it works?
                    // FIXME both Uris works with Google Drive app. Why both? What's the difference? How about other apps like DropBox?
                    final Bundle extras = data.getExtras();
                    if (extras != null && extras.containsKey(Intent.EXTRA_STREAM))
                        mFileStreamUri = extras.getParcelable(Intent.EXTRA_STREAM);

                    // file name and size must be obtained from Content Provider
                    final Bundle bundle = new Bundle();
                    bundle.putParcelable(EXTRA_URI, uri);
                    getLoaderManager().restartLoader(0, bundle, this);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    //do nothing
                    Toast.makeText(this, "Bt is enabled!", Toast.LENGTH_LONG).show();
                } else {
                    // User did not enable Bluetooth or an error occured
                    if(D) Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bt is not enabled!", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }

    public boolean LoadFileInfo(String path) {
        // check the file path
        if (TextUtils.isEmpty(path) == true) {
            if(D) Log.e("TAG", "the file path string is null");
            return false;
        }

        // check the file type
        if( MimeTypeMap.getFileExtensionFromUrl(path).equalsIgnoreCase("BIN") != true) {
            mStatusOk = false;
            if(D) Log.e("TAG", "the file type is not right");
            return false;
        }
        //get the new firmware version
        try {
            mBinInputStream = openInputStream(path);
        }catch (final IOException e){
            if(D) Log.e(TAG, "An exception occurred while opening file", e);
            return false;
        }
        newFwVersion = mBinInputStream.binFileVersion();
        if(D) Log.d(TAG, "newFwVersion = " + newFwVersion);
        // close the file
        if(mBinInputStream != null){
            try {
                mBinInputStream.close();
                mBinInputStream = null;
            }catch (IOException e){
                if(D) Log.e(TAG, "error in close file", e);
                return false;
            }
        }

        mStatusOk = true;
        mFilePath = path;
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        final Uri uri = args.getParcelable(EXTRA_URI);
        final String[] projection = new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA};
        return new CursorLoader(this, uri, projection, null, null, null);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        mFileNameView.setText(null);
        mFileSizeView.setText(null);
        mFilePath = null;
        mFileStreamUri = null;
        mStatusOk = false;
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        if (data.moveToNext()) {
            final String fileName = data.getString(0 /* DISPLAY_NAME */);
            final int fileSize = data.getInt(1 /* SIZE */);
            final String filePath = data.getString(2 /* DATA */);
            // load the file
            if(LoadFileInfo(filePath) == true) {
                // send msg
                Log.d(TAG, filePath);
                mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_SUCCESS));
            } else {
                showToast("something error in load file");
                // send msg
                mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_FAIL));
            }

        }
    }

    /**
     * Opens the binary input stream from a BIN file. A Path to the BIN file is given.
     *
     * @param filePath the path to the BIN file
     * @return the binary input stream with BIN data
     * @throws IOException
     */
    private BinInputStream openInputStream(final String filePath) throws IOException {
        final InputStream is = new FileInputStream(filePath);
        return new BinInputStream(is);
    }

    public void onSelectFile() {
        // Clear file info.
        Log.d("ldsbug", "onselectfileclicked");
        mFileNameView.setText(null);
        mFileSizeView.setText(null);
        mFileVersionView.setText(null);
        mFileStatusView.setText(R.string.dfu_file_status_no_file);
        mUploadButton.setEnabled(false);

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("file/*.bin");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // file browser has been found on the device
            // lds
            //startActivityForResult(intent, SELECT_FILE_REQ);
            mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_SUCCESS));
        }
    }

    /**
     * Called when Select File was pressed
     *
     * @param view a button that was pressed
     */
    public void onSelectFileClicked(final View view) {
        // Clear file info.
        Log.d("ldsbug", "onselectfileclicked");
        mFileNameView.setText(null);
        mFileSizeView.setText(null);
        mFileVersionView.setText(null);
        mFileStatusView.setText(R.string.dfu_file_status_no_file);
        mUploadButton.setEnabled(false);

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("file/*.bin");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // file browser has been found on the device
            // lds
            //startActivityForResult(intent, SELECT_FILE_REQ);
            mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_SUCCESS));
        } else {
            // there is no any file browser app, let's try to download one
            final View customView = getLayoutInflater().inflate(R.layout.app_file_browser, null);
            final ListView appsList = (ListView) customView.findViewById(android.R.id.list);
            appsList.setAdapter(new FileBrowserAppsAdapter(this));
            appsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            appsList.setItemChecked(0, true);
            new AlertDialog.Builder(this).setTitle(R.string.dfu_alert_no_filebrowser_title).setView(customView).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    dialog.dismiss();
                }
            }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    final int pos = appsList.getCheckedItemPosition();
                    if (pos >= 0) {
                        final String query = getResources().getStringArray(R.array.dfu_app_file_browser_action)[pos];
                        Log.d("ldsbug", query);
                        final Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(query));
                        startActivity(storeIntent);
                    }
                }
            }).show();
        }
    }

    /**
     * Callback of UPDATE/CANCEL button on FeaturesActivity
     * Button.onClick convert to onUploadClicked
     */
    public void onUploadClicked(final View view) {
        mUpdateSuccessful = false;
        if(mUploadButton.getText().toString().equals(getString(R.string.dfu_action_upload))) {
            if(dfu == null) {
                showToast("the realsil dfu didn't ready");
                if(D) Log.e(TAG, "the realsil dfu didn't ready");
                return;
            }

            // set the total speed for android 4.4, to escape the internal error
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                dfu.setSpeedControl(true, 1000);
            }
            // Use GlobalGatt do not need to disconnect, just unregister the callback
            mGlobalGatt.unRegisterCallback(mDeviceAddress, mBluetoothGattCallback);
            /*
            // disconnect the gatt
            disconnect(mBtGatt);// be care here
            // wait a while for close gatt.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
            if(D) Log.e(TAG, "Start OTA, address is: " + mDeviceAddress);
            if(dfu.start(mDeviceAddress, mFilePath)){
                showToast("start OTA update");
                if(D) Log.d(TAG, "true");
            } else {
                showToast("something error in device info or the file");
                if(D) Log.e(TAG, "something error in device info or the file, false");
            }

            showProgressBar();
            mProgressBar.setIndeterminate(true);
            mUploadButton.setEnabled(false);
            mSelectTargetButton.setEnabled(false);
        }
    }

    /**
     * Called when Select Target was pressed.
     * @param view
     */
    public void onSelectTargetClicked(final View view){
        showToast("selected target device");
        if (isFastClick()) {
            if(D) Log.w(TAG, "click too fast.");
            return ;
        }
        mGlobalGatt.closeAll();
        mBtGatt = null;
        // Clear show info.
        mFileNameView.setText(null);
        mFileSizeView.setText(null);
        mFileVersionView.setText(null);
        mFileStatusView.setText(R.string.dfu_file_status_no_file);
        mUploadButton.setEnabled(false);
        mTargetVersionView.setText(null);
        mPatchVersionView.setText(null);

        mSelectFileButton.setEnabled(false);
        mUploadButton.setEnabled(false);
        showSelectDevicesDialog();
    }
    // escape fast click
    private static long lastClickTime;
    public synchronized static boolean isFastClick() {
        long time = System.currentTimeMillis();
        if ( time - lastClickTime < 500) {
            return true;
        }
        lastClickTime = time;
        return false;
    }

    private void showProgressBar() {
        mProgressBar.setVisibility(View.VISIBLE);
        mTextPercentage.setVisibility(View.VISIBLE);
        mTextUploading.setVisibility(View.VISIBLE);
        mSelectFileButton.setEnabled(false);
        mUploadButton.setEnabled(false);
        mTextPercentage.setText(null);
        mTextUploading.setText(null);
    }

    private void clearUI() {
        mProgressBar.setVisibility(View.INVISIBLE);
        mTextPercentage.setVisibility(View.INVISIBLE);
        mTextUploading.setVisibility(View.INVISIBLE);
        mSelectFileButton.setEnabled(false);
        mUploadButton.setEnabled(false);
        mUploadButton.setText(R.string.dfu_action_upload);
        mSelectTargetButton.setEnabled(true);
        mSelectTargetButton.requestFocus();
        mFileNameView.setText(null);
        mFileSizeView.setText(null);
        mFileVersionView.setText(null);
        mTargetVersionView.setText(null);
        mPatchVersionView.setText(null);
        mFileStatusView.setText(R.string.dfu_file_status_no_file);
        mFilePath = null;
        mFileStreamUri = null;
        mStatusOk = false;
    }
    RealsilDfuCallback cb = new RealsilDfuCallback() {
        public void onServiceConnectionStateChange(boolean status, RealsilDfu d) {
            if(D) Log.e(TAG, "status: " + status);
            if(status == true) {
                Toast.makeText(getApplicationContext(), "DFU Service connected", Toast.LENGTH_SHORT).show();
                dfu = d;
            } else {
                Toast.makeText(getApplicationContext(), "DFU Service disconnected", Toast.LENGTH_SHORT).show();
                dfu = null;
            }
        }

        public void onError(int e) {
            if(D) Log.e(TAG, "onError: " + e);

            // send msg to update ui
            Message msg = mHandle.obtainMessage(OTA_CALLBACK_ERROR);
            msg.arg1 = e;
            mHandle.sendMessage(msg);
        }

        public void onSucess(int s) {
            if(D) Log.e(TAG, "onSucess: " + s);

            // send msg to update ui
            Message msg = mHandle.obtainMessage(OTA_CALLBACK_SUCCESS);
            msg.arg1 = s;
            mHandle.sendMessage(msg);

        }

        public void onProcessStateChanged(int state) {
            if(D) Log.e(TAG, "onProcessStateChanged: " + state);

            // send msg to update ui
            Message msg = mHandle.obtainMessage(OTA_CALLBACK_STATE_CHANGE);
            msg.arg1 = state;
            mHandle.sendMessage(msg);
        }

        public void onProgressChanged(int progress) {
            if(D) Log.e(TAG, "onProgressChanged: " + progress);

            // send msg to update ui
            Message msg = mHandle.obtainMessage(OTA_CALLBACK_PROCESS_CHANGE);
            msg.arg1 = progress;
            mHandle.sendMessage(msg);
        }
    };

    private void showVersionCheckDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("버전 확인 대화 상자");
        builder.setMessage("현재 버전이 낮습니다. 업데이트 하시겠습니까?");
        builder.setCancelable(true);
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                mHandle.sendMessage(mHandle.obtainMessage(OTA_GET_FILE_INFO_SUCCESS));
                //onUploadClicked() : 업로드 클릭 시 이벤트
                mUpdateSuccessful = false;
                if(mUploadButton.getText().toString().equals(getString(R.string.dfu_action_upload))) {
                    if(dfu == null) {
                        showToast("the realsil dfu didn't ready");
                        if(D) Log.e(TAG, "the realsil dfu didn't ready");
                        return;
                    }

                    // set the total speed for android 4.4, to escape the internal error
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        dfu.setSpeedControl(true, 1000);
                    }
                    // Use GlobalGatt do not need to disconnect, just unregister the callback
                    mGlobalGatt.unRegisterCallback(mDeviceAddress, mBluetoothGattCallback);
            /*
            // disconnect the gatt
            disconnect(mBtGatt);// be care here
            // wait a while for close gatt.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
                    if(D) Log.e(TAG, "Start OTA, address is: " + mDeviceAddress);
                    if(dfu.start(mDeviceAddress, mFilePath)){
                        showToast("start OTA update");
                        if(D) Log.d(TAG, "true");
                    } else {
                        showToast("something error in device info or the file");
                        if(D) Log.e(TAG, "something error in device info or the file, false");
                    }

                    showProgressBar();
                    mProgressBar.setIndeterminate(true);
                    mUploadButton.setEnabled(false);
                    mSelectTargetButton.setEnabled(false);
                }
        }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showToast("OTA 종료");
                finish();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

}
