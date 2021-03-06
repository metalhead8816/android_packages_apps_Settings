/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Environment;
import android.os.IMountService;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.format.Formatter;
import android.util.Log;

import com.android.settings.R;

import java.io.File;
import java.text.DecimalFormat;

public class Memory extends PreferenceActivity {

    private static final String EXTRA_SD_STORAGE_PATH = "/system/sd";

    private static final String TAG = "Memory";

    private static final String MEMORY_SD_SIZE = "memory_sd_size";

    private static final String MEMORY_SD_AVAIL = "memory_sd_avail";

    private static final String MEMORY_SD_EXTRA_SIZE = "memory_sd_extra_size";

    private static final String MEMORY_SD_EXTRA_AVAIL = "memory_sd_extra_avail";

    private static final String MEMORY_SD_UNMOUNT = "memory_sd_unmount";

    private static final String MEMORY_SD_FORMAT = "memory_sd_format";
    
    private static final String MEMORY_SD_EXTRA_CATEGORY = "sd_extra_memory";
    
    private Resources mRes;

    private Preference mSdSize;
    private Preference mSdAvail;
    private Preference mSdExtraSize;
    private Preference mSdExtraAvail;
    private Preference mSdUnmount;
    private Preference mSdFormat;
    
    // Access using getMountService()
    private IMountService mMountService = null;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        addPreferencesFromResource(R.xml.device_info_memory);
        
        mRes = getResources();
        mSdSize = findPreference(MEMORY_SD_SIZE);
        mSdAvail = findPreference(MEMORY_SD_AVAIL);
        mSdExtraSize = findPreference(MEMORY_SD_EXTRA_SIZE);
        mSdExtraAvail = findPreference(MEMORY_SD_EXTRA_AVAIL);
        mSdUnmount = findPreference(MEMORY_SD_UNMOUNT);
        mSdFormat = findPreference(MEMORY_SD_FORMAT);
        
        if (!SystemProperties.getBoolean("cm.a2sd.active", false)) {
        	this.getPreferenceScreen().removePreference(findPreference(MEMORY_SD_EXTRA_CATEGORY));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        intentFilter.addAction(Intent.ACTION_MEDIA_NOFS);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        
        updateMemoryStatus();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }
    
    private synchronized IMountService getMountService() {
       if (mMountService == null) {
           IBinder service = ServiceManager.getService("mount");
           if (service != null) {
               mMountService = IMountService.Stub.asInterface(service);
           } else {
               Log.e(TAG, "Can't get mount service");
           }
       }
       return mMountService;
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSdUnmount) {
            unmount();
            return true;
        } else if (preference == mSdFormat) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClass(this, com.android.settings.MediaFormat.class);
            startActivity(intent);
            return true;
        }
        
        return false;
    }
     
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateMemoryStatus();
        }
    };

    private void unmount() {
        IMountService mountService = getMountService();
        try {
            if (mountService != null) {
                mountService.unmountMedia(Environment.getExternalStorageDirectory().toString());
            } else {
                Log.e(TAG, "Mount service is null, can't unmount");
            }
        } catch (RemoteException ex) {
            // Failed for some reason, try to update UI to actual state
            updateMemoryStatus();
        }
    }

    private void updateMemoryStatus() {
        String status = Environment.getExternalStorageState();
        String readOnly = "";
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            status = Environment.MEDIA_MOUNTED;
            readOnly = mRes.getString(R.string.read_only);
        }
 
        mSdFormat.setEnabled(false);

        if (status.equals(Environment.MEDIA_MOUNTED)) {
            try {
                File path = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long totalBlocks = stat.getBlockCount();
                long availableBlocks = stat.getAvailableBlocks();
                
                mSdSize.setSummary(formatSize(totalBlocks * blockSize));
                mSdAvail.setSummary(formatSize(availableBlocks * blockSize) + readOnly);
                
                boolean a2sdDisabled = SystemProperties.get("cm.a2sd.active", "0").equals("0");

                mSdUnmount.setEnabled(a2sdDisabled);

                if (a2sdDisabled) {
                    mSdExtraSize.setSummary(mRes.getString(R.string.sd_unavailable));
                    mSdExtraAvail.setSummary(mRes.getString(R.string.sd_unavailable));
                } else {
                    File extraPath = new File(EXTRA_SD_STORAGE_PATH);
                    StatFs extraStat = new StatFs(extraPath.getPath());
                    long eBlockSize = extraStat.getBlockSize();
                    long eTotalBlocks = extraStat.getBlockCount();
                    long eAvailableBlocks = extraStat.getAvailableBlocks();

                    mSdExtraSize.setSummary(formatSize(eTotalBlocks * eBlockSize));
                    mSdExtraAvail.setSummary(formatSize(eAvailableBlocks * eBlockSize) + readOnly);
                }
            } catch (IllegalArgumentException e) {
                // this can occur if the SD card is removed, but we haven't received the 
                // ACTION_MEDIA_REMOVED Intent yet.
                status = Environment.MEDIA_REMOVED;
            }
            
        } else {
            mSdSize.setSummary(mRes.getString(R.string.sd_unavailable));
            mSdAvail.setSummary(mRes.getString(R.string.sd_unavailable));
            mSdExtraSize.setSummary(mRes.getString(R.string.sd_unavailable));
            mSdExtraAvail.setSummary(mRes.getString(R.string.sd_unavailable));

            mSdUnmount.setEnabled(false);

            if (status.equals(Environment.MEDIA_UNMOUNTED) ||
                status.equals(Environment.MEDIA_NOFS) ||
                status.equals(Environment.MEDIA_UNMOUNTABLE) ) {
                mSdFormat.setEnabled(true);
            }

            
        }

        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        findPreference("memory_internal_avail").setSummary(formatSize(availableBlocks * blockSize));
    }
    
    private String formatSize(long size) {
        return Formatter.formatFileSize(this, size);
    }
    
}
