package com.cyngn.audiofx.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import com.cyanogen.ambient.analytics.Event;
import com.cyngn.audiofx.AudioFxApplication;
import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.fragment.AudioFxFragment;
import com.cyngn.audiofx.fragment.DTSFragment;
import com.cyngn.audiofx.knobs.KnobCommander;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.DtsControl;
import com.cyngn.audiofx.stats.AppState;
import com.cyngn.audiofx.stats.UserSession;

import java.util.ArrayList;
import java.util.List;

public class ActivityMusic extends Activity {

    private static final String TAG = ActivityMusic.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final int CURRENT_MODE_AUDIOFX = 1;
    public static final int CURRENT_MODE_DTS = 2;

    public static final String TAG_AUDIOFX = "audiofx";
    public static final String TAG_DTS = "dts";
    public static final String EXTRA_CALLING_PACKAGE = "audiofx::extra_calling_package";

    private int mCurrentMode = CURRENT_MODE_AUDIOFX;
    private Spinner mSpinner;
    DtsControl mDts;

    private CheckBox mCurrentDeviceToggle;
    MasterConfigControl mConfig;
    String mCallingPackage;

    private List<ActivityStateListener> mGlobalToggleListeners = new ArrayList<>();

    private boolean mWaitingForService = true;
    private SharedPreferences.OnSharedPreferenceChangeListener mServiceReadyObserver;

    public interface ActivityStateListener {
        public void onGlobalToggleChanged(final CompoundButton buttonView, boolean isChecked);

        public void onModeChanged(int mode);
    }

    private CompoundButton.OnCheckedChangeListener mGlobalEnableToggleListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView,
                                     final boolean isChecked) {
            if (UserSession.getInstance() != null) {
                UserSession.getInstance().deviceEnabledDisabled();
            }
            for (ActivityStateListener listener : mGlobalToggleListeners) {
                listener.onGlobalToggleChanged(buttonView, isChecked);
            }
        }
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (DEBUG)
            Log.i(TAG, "onCreate() called with "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCallingPackage = getIntent().getStringExtra(EXTRA_CALLING_PACKAGE);
        Log.i(TAG, "calling package: " + mCallingPackage);

        final SharedPreferences globalPrefs = Constants.getGlobalPrefs(this);
        final boolean ready = globalPrefs
                .getBoolean(Constants.SAVED_DEFAULTS, false);

        mWaitingForService = !ready;
        if (mWaitingForService) {
            mServiceReadyObserver = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    if (key.equals(Constants.SAVED_DEFAULTS)
                            && sharedPreferences.getBoolean(Constants.SAVED_DEFAULTS,
                            false)) {
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
                        init(savedInstanceState);
                        setupDtsActionBar();

                        mWaitingForService = false;
                        invalidateOptionsMenu();
                        mServiceReadyObserver = null;
                    }
                }
            };
            globalPrefs.registerOnSharedPreferenceChangeListener(mServiceReadyObserver);
            startService(new Intent(ActivityMusic.this, AudioFxService.class));
            // TODO add loading fragment if service initialization takes too long
        } else {
            init(savedInstanceState);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // should null it out if one was there, compat redirector with package will go through onCreate
        mCallingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE);
    }

    @Override
    protected void onDestroy() {
        if (mServiceReadyObserver != null) {
            Constants.getGlobalPrefs(this)
                    .unregisterOnSharedPreferenceChangeListener(mServiceReadyObserver);
            mServiceReadyObserver = null;
        }
        super.onDestroy();
    }

    private void init(Bundle savedInstanceState) {
        mConfig = MasterConfigControl.getInstance(this);
        mDts = new DtsControl(this);

        ActionBar ab = getActionBar();
        ab.setTitle(R.string.app_title);
        ab.setDisplayShowCustomEnabled(true);

        if (mDts.hasDts()) {
            ab.setCustomView(R.layout.action_bar);

            mCurrentDeviceToggle = (CheckBox) findViewById(R.id.device_toggle);
            final int padding = getResources().getDimensionPixelSize(
                    R.dimen.action_bar_switch_padding);
            mCurrentDeviceToggle.setPaddingRelative(mCurrentDeviceToggle.getPaddingLeft(),
                    mCurrentDeviceToggle.getPaddingTop(),
                    padding,
                    mCurrentDeviceToggle.getPaddingBottom());

            mCurrentDeviceToggle.setButtonDrawable(R.drawable.toggle_check);
            mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);

            ModeAdapter spinnerAdapter = new ModeAdapter(this);

            mSpinner = (Spinner) findViewById(R.id.spinner);
            mSpinner.setAdapter(spinnerAdapter);

            final boolean shouldUseDts = mDts.shouldUseDts();
            setCurrentMode(shouldUseDts ? CURRENT_MODE_DTS : CURRENT_MODE_AUDIOFX);
            mSpinner.setSelection(shouldUseDts ? 1 : 0, false);
        } else {
            // manually populate action bar
            if (mConfig.hasMaxxAudio()) {
                ab.setDisplayShowTitleEnabled(true);
                ab.setSubtitle(R.string.app_subtitle);
            }
            // setup actionbar on off switch
            mCurrentDeviceToggle = new CheckBox(this);
            final int padding = getResources().getDimensionPixelSize(
                    R.dimen.action_bar_switch_padding);
            mCurrentDeviceToggle.setPaddingRelative(0, 0, padding, 0);
            mCurrentDeviceToggle.setButtonDrawable(R.drawable.toggle_check);
            mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);

            final ActionBar.LayoutParams params = new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.END);
            ab.setCustomView(mCurrentDeviceToggle, params);
        }

        if (savedInstanceState == null && findViewById(R.id.main_fragment) != null) {
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.main_fragment,
                            mCurrentMode == CURRENT_MODE_AUDIOFX
                                    ? new AudioFxFragment()
                                    : new DTSFragment(),
                            TAG_AUDIOFX)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mWaitingForService && mDts.hasDts()) {
            final boolean audioFX = mCurrentMode == CURRENT_MODE_AUDIOFX;
            final int padding = getResources().getDimensionPixelSize(
                    audioFX ? R.dimen.action_bar_switch_padding
                            : R.dimen.action_bar_dts_switch_padding);

            mCurrentDeviceToggle.setPaddingRelative(mCurrentDeviceToggle.getPaddingLeft(),
                    mCurrentDeviceToggle.getPaddingTop(),
                    padding,
                    mCurrentDeviceToggle.getPaddingBottom());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.i(TAG, "onResume() called with " + "");
        super.onResume();

        // initiate a new session
        new UserSession(mCallingPackage);

        if (mWaitingForService) {
            return;
        }

        // action bar controls need to live beyond all fragments
        setupDtsActionBar();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (DEBUG) Log.d(TAG, "Session: " + UserSession.getInstance());

        final Event.Builder builder = new Event.Builder("session", "ended");
        UserSession.getInstance().append(builder);
        AppState.appendState(mConfig, KnobCommander.getInstance(this), builder);
        ((AudioFxApplication)getApplicationContext()).sendEvent(builder.build());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (DEBUG) Log.i(TAG, "onConfigurationChanged() called with "
                + "newConfig = [" + newConfig + "]");
        if (newConfig.orientation != getResources().getConfiguration().orientation) {
            mCurrentDeviceToggle = null;
        }
    }

    private void setupDtsActionBar() {
        if (!mDts.hasDts()) {
            return;
        }
        mCurrentDeviceToggle = (CheckBox) findViewById(R.id.device_toggle);

        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedMode = (Integer) parent.getItemAtPosition(position);
                setCurrentMode(selectedMode);

                mDts.setShouldUseDts(mCurrentMode == CURRENT_MODE_DTS);

                if (mCurrentMode == CURRENT_MODE_AUDIOFX) {
                    mCurrentDeviceToggle.setChecked(mConfig.isCurrentDeviceEnabled());

                    // change to audio fx layout
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_fragment, new AudioFxFragment(), TAG_AUDIOFX)
                            .commit();

                } else if (mCurrentMode == CURRENT_MODE_DTS) {
                    // change to dts layout
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_fragment, new DTSFragment(), TAG_DTS)
                            .commit();
                }
                mCurrentDeviceToggle = null;
                setupDtsActionBar();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void addToggleListener(ActivityStateListener listener) {
        mGlobalToggleListeners.add(listener);
    }

    public void removeToggleListener(ActivityStateListener listener) {
        mGlobalToggleListeners.remove(listener);
    }

    public void setGlobalToggleChecked(boolean checked) {
        if (mCurrentDeviceToggle != null) {
            mCurrentDeviceToggle.setOnCheckedChangeListener(null);
            mCurrentDeviceToggle.setChecked(checked);
            mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);
        }
    }

    public void setCurrentMode(int currentMode) {
        if (mCurrentMode != currentMode) {
            mCurrentMode = currentMode;
            for (ActivityStateListener listener : mGlobalToggleListeners) {
                listener.onModeChanged(mCurrentMode);
            }
        }
    }

    public int getCurrentMode() {
        return mCurrentMode;
    }

    private static class ModeAdapter extends ArrayAdapter<Integer> {
        private LayoutInflater mInflater;
        public ModeAdapter(Context context) {
            super(context, R.layout.action_bar_spinner_row, android.R.id.title,
                    new Integer[]{CURRENT_MODE_AUDIOFX, CURRENT_MODE_DTS}
            );
            setDropDownViewResource(R.layout.action_bar_spinner_row);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view;
            TextView text;
            TextView subText;

            if (convertView == null) {
                view = mInflater.inflate(R.layout.action_bar_spinner_row, parent, false);
            } else {
                view = convertView;
            }
            final Integer mode = getItem(position);

            text = (TextView) view.findViewById(android.R.id.title);
            text.setText(R.string.app_name);

            subText = (TextView) view.findViewById(android.R.id.summary);
            subText.setText(getModeSubTitle(mode));
            subText.setVisibility(subText.length() == 0 ? View.GONE : View.VISIBLE);
            return view;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            TextView text;
            TextView subText;

            if (convertView == null) {
                view = mInflater.inflate(R.layout.action_bar_spinner, parent, false);
            } else {
                view = convertView;
            }

            final Integer mode = getItem(position);
            text = (TextView) view.findViewById(android.R.id.title);
            text.setText(R.string.app_name);

            subText = (TextView) view.findViewById(android.R.id.summary);
            subText.setText(getModeSubTitle(mode));
            subText.setVisibility(subText.length() == 0 ? View.GONE : View.VISIBLE);
            return view;
        }

        public String getModeSubTitle(int mode) {
            switch (mode) {
                case CURRENT_MODE_DTS:
                    return getContext().getResources().getString(R.string.mode_dts);
                case CURRENT_MODE_AUDIOFX:
                default:
                    return null;
            }
        }
    }
}
