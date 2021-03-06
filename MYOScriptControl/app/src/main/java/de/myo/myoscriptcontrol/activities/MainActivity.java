package de.myo.myoscriptcontrol.activities;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.thalmic.myo.Hub;
import com.thalmic.myo.Pose;
import com.thalmic.myo.scanner.ScanActivity;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import de.myo.myoscriptcontrol.listmanagement.gesturemanagement.GestureItem;
import de.myo.myoscriptcontrol.GestureScriptManager;
import de.myo.myoscriptcontrol.R;
import de.myo.myoscriptcontrol.scriptexecution.SL4AManager;
import de.myo.myoscriptcontrol.listmanagement.scriptmanagement.ScriptItem;
import de.myo.myoscriptcontrol.gesturerecording.GesturePattern;
import de.myo.myoscriptcontrol.gesturerecording.GridPosition;
import de.myo.myoscriptcontrol.gesturerecording.*;

public class MainActivity extends ActionBarActivity implements ListenerTarget {

    private String ConfigDir;
    private File ConfigFile;
    public static String ScriptDir;
    private static Hub mMyoHub;

    private MYOStatus mStatus = MYOStatus.UNKNOWN;
    private GesturePattern mPattern = new GesturePattern();
    private Pose mPose;
    private boolean mExecutionMode = false;
    private GridPosition mCurrentPosition;
    private GesturePatternGridViewAdapter mGesturePatternGridViewAdapter;
    public static boolean mDebugMode = false;


    private void initSwitchListener(){
        Switch switchMode = (Switch)findViewById(R.id.switchDebugMode);
        switchMode.setChecked(mDebugMode);
        switchMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDebugMode = isChecked;
                if(isChecked) {
                    findViewById(R.id.debugGridView).setVisibility(View.VISIBLE);
                    showPattern();
                }
                else {
                    findViewById(R.id.debugGridView).setVisibility(View.INVISIBLE);
                }
            }
        });

    }

    private void initializeFiles() {
        ConfigDir = getMyoFileDir("config/");
        ConfigFile = new File(ConfigDir, "Config.json");
        ScriptDir = getMyoFileDir("scripts/");
    }

    private String getMyoFileDir(String folder) {
        String fileDirPath = getFilesDir().getAbsolutePath();
        File file = new File(fileDirPath + "/" + folder);
        file.mkdir();
        if (!file.isDirectory()) {
            ErrorActivity.handleError(this, "Die benötigten Ordner für die Anwendung konnten nicht angelegt werden.");
        }
        return file.getAbsolutePath();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GestureRecordDeviceListener.getInstance().removeTarget(this);
    }

    private void initializeMYOHub() {
        GestureRecordDeviceListener.getInstance().addTarget(this);
        mMyoHub = Hub.getInstance();
        try {
            if (!mMyoHub.init(this)) {
                Toast.makeText(getApplicationContext(), "Could not initialize MYO Hub", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        initializeMYOListenerForHub(mMyoHub);
    }

    private void initializeMYOListenerForHub(Hub hub) {
        try {
            hub.removeListener(GestureRecordDeviceListener.getInstance());
            hub.addListener(GestureRecordDeviceListener.getInstance());
            hub.setLockingPolicy(Hub.LockingPolicy.STANDARD);
            if (hub.getConnectedDevices().size() == 0) {
                hub.attachToAdjacentMyo();
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Could not initialize MYO Listener", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeFiles();
        initSwitchListener();
        try {
            GestureScriptManager.getInstance().setConfigFile(ConfigFile);
            mStatus = MYOStatus.DISCONNECTED;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            ErrorActivity.handleError(this, e.getMessage());
        }
        initializeMYOHub();
        initGridAdapter();
        OnUpdateStatus(GestureRecordDeviceListener.getInstance().getStatus());
    }

    private void initGridAdapter() {
        if (mPattern!=null) {
            GridView grid = (GridView)findViewById(R.id.debugGridView);
            mGesturePatternGridViewAdapter = new GesturePatternGridViewAdapter(this, mPattern);
            grid.setAdapter(mGesturePatternGridViewAdapter);
        }
    }

    // TKi 30.08.2015
    @Override
    protected void onResume() {
        super.onResume();
        OnUpdateStatus(GestureRecordDeviceListener.getInstance().getStatus());
        mExecutionMode = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        mExecutionMode = false;
        if (id == R.id.action_gesture_manager) {
            Intent intent = new Intent(MainActivity.this, GestureListActivity.class);

            startActivity(intent);
            return true;
        } else if (id == R.id.action_script_manager) {
            Intent intent = new Intent(MainActivity.this, ScriptListActivity.class);

            startActivity(intent);
            return true;
        } else if (id == R.id.action_connect_myo) {
            Intent intent = new Intent(MainActivity.this, ScanActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // TKi 26.03.2015
    public void checkRecordedPatternForAvailableScript(GesturePattern recordedPattern) {
        ArrayList<GestureItem> gestureList = GestureScriptManager.getInstance().getGestureList();
        int counterNoEqualGestureItem = 0;
        for (GestureItem gestureItem : gestureList) {
            if (gestureItem.equalPattern(recordedPattern)) {
                if (gestureItem.hasScript())
                    try {
                        UUID uuid = UUID.fromString(gestureItem.getScript());
                        ScriptItem scriptItem = GestureScriptManager.getInstance().getScriptByUUID(uuid);
                        executeScript(scriptItem);
                    } catch(IllegalArgumentException e){
                        e.printStackTrace();
                        ErrorActivity.handleError(this, e.getMessage());
                } else {
                    String message = "Der Geste "+ gestureItem.getName() +" ist kein Skript zugeordnet.";
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                }
            } else {
                counterNoEqualGestureItem++;
            }
        }
        if (counterNoEqualGestureItem == gestureList.size()) {
            Toast.makeText(getApplicationContext(), "Die ausgeführe Geste existiert noch nicht.", Toast.LENGTH_SHORT).show();
        }
    }


    // DTh 06.04.2015
    private void executeScript(ScriptItem scriptItem){
        try {
            SL4AManager.startScript(getApplicationContext(), this, scriptItem);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            ErrorActivity.handleError(this, e.getMessage());
        }
    }

    // TKi 28.03.2015
    @Override
    public void OnPose(Pose pose) {
        mPose = pose;
        if(mPattern == null){
            mPattern = new GesturePattern();
        }
        if(mPose == Pose.DOUBLE_TAP) {
            OnUpdateStatus(MYOStatus.IDLE);
            mPattern.clear();
            showPattern();
        }
        if(mExecutionMode) {
            if (mPose == Pose.FIST) {
                mPattern.add(mCurrentPosition);
                if (mDebugMode) {
                    showPattern();
                }
            }
            if (mPose == Pose.FINGERS_SPREAD) {
                if (!mPattern.isEmpty()) {
                    checkRecordedPatternForAvailableScript(mPattern);
                }
                OnUpdateStatus(MYOStatus.LOCKED);
                showPattern();
            }
            if (mPose == Pose.WAVE_OUT) {
                mPattern.clear();
                showPattern();
            }
        }
    }

    private void showPattern(){
        if (mPattern!=null && mDebugMode) {
            mGesturePatternGridViewAdapter.notifyDataSetChanged();
        }
    }

    // TKi 28.03.2015
    @Override
    public void OnGridPositionUpdate(GridPosition position) {
        if(mExecutionMode) {
            mCurrentPosition = position;
        }
    }

    // TKi 28.03.2015
    @Override
    public void OnUpdateStatus(MYOStatus status) {
        mStatus = status;
        updateStatus();
    }

    // TKi 28.03.2015
    private void updateStatus(){
        ((TextView)findViewById(R.id.textViewMainStatus)).setText("Verbindungsstatus: " + mStatus.toString());
        if(mStatus== MYOStatus.DISCONNECTED){
            ((ImageView) findViewById(R.id.imageViewMainStatus)).setImageResource(android.R.color.holo_red_light);
            mPattern.clear();
            showPattern();
        }
        if(mStatus== MYOStatus.UNSYNCED) {
            ((ImageView) findViewById(R.id.imageViewMainStatus)).setImageResource(android.R.color.holo_orange_dark);
            mPattern.clear();
            showPattern();
        }
        if(mStatus== MYOStatus.LOCKED) {
            ((ImageView) findViewById(R.id.imageViewMainStatus)).setImageResource(android.R.color.holo_orange_light);
            mPattern.clear();
            showPattern();
        }
        if(mStatus== MYOStatus.IDLE) {
            ((ImageView) findViewById(R.id.imageViewMainStatus)).setImageResource(android.R.color.holo_green_light);
        }
        if(mStatus== MYOStatus.UNKNOWN){
            ((ImageView) findViewById(R.id.imageViewMainStatus)).setImageResource(android.R.color.holo_red_light);
        }

    }
    // TKi 04.04.2015
    public GesturePattern getPattern() {
        return mPattern;
    }

    // TKi 04.04.2015
    public void setExecutionMode(boolean mExecutionMode) {
        this.mExecutionMode = mExecutionMode;
    }

    // TKi 04.04.2015
    public boolean getExecutionMode() {
        return mExecutionMode;
    }

    // TKi 04.04.2015
    public MYOStatus getStatus() {
        return mStatus;
    }

    // TKi 04.04.2015
    public void setCurrentPosition(GridPosition gridPosition){ this.mCurrentPosition = gridPosition; }
}
