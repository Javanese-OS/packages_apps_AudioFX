/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package com.cyngn.audiofx;

import android.app.Application;
import android.content.Intent;

import android.util.Log;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.analytics.Event;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyngn.audiofx.service.AudioFxService;

public class AudioFxApplication extends Application {

    private static final String TAG = AudioFxApplication.class.getSimpleName();
    private static final boolean DEBUG = false;

    private AmbientApiClient mClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mClient = new AmbientApiClient.Builder(this)
                .addApi(AnalyticsServices.API)
                .build();
        mClient.connect();
    }

    public void sendEvent(Event event) {
        if (DEBUG) {
            Log.i(TAG, "sendEvent() called with event = [" + event + "]");
        }
        AnalyticsServices.AnalyticsApi.sendEvent(mClient, event);
    }
}
