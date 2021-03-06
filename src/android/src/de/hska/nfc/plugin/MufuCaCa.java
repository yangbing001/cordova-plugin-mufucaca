package de.hska.nfc.plugin;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.util.Log;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.hska.nfc.plugin.Wallet.ReadCardResult;

// using wildcard imports so we can support Cordova 3.x

public class MufuCaCa extends CordovaPlugin implements AsyncResultInterface {
    private static final String REGISTER_DEFAULT_TAG = "registerTag";
    private static final String REMOVE_DEFAULT_TAG = "removeTag";
    private static final String ADD_RESULT_LISTENER = "addResultListener";
    private static final String REMOVE_RESULT_LISTENER = "removeResultListener";
    private static final String ADD_ADAPTER_STATE_LISTENER = "addAdapterStateListener";
    private static final String REMOVE_ADAPTER_STATE_LISTENER = "removeAdapterStateListener";
    private static final String ADAPTER_STATE = "adapterState";
    private static final String ENABLED = "enabled";
    private static final String INIT = "init";
    private static final String SHOW_SETTINGS = "showSettings";

    private static final String TAG_DEFAULT = "tag";

    private static final String STATUS_NFC_OK = "NFC_OK";
    private static final String STATUS_NO_NFC = "NO_NFC";
    private static final String STATUS_NFC_DISABLED = "NFC_DISABLED";

    private static final String TAG = "MufuCaCa";
    public static final String READ_RESULT = "readResult";
    private final List<IntentFilter> intentFilters = new ArrayList<IntentFilter>();
    private final ArrayList<String[]> techLists = new ArrayList<String[]>();

    private PendingIntent pendingIntent = null;

    private Intent savedIntent = null;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "execute " + action);

        // showSettings can be called if NFC is disabled
        // might want to skip this if NO_NFC
        if (action.equalsIgnoreCase(SHOW_SETTINGS)) {
            showSettings(callbackContext);
            return true;
        } else if (action.equals(ADD_ADAPTER_STATE_LISTENER)) {
            addAdapterStateListener(callbackContext);
        } else if (action.equals(REMOVE_ADAPTER_STATE_LISTENER)) {
            removeAdapterStateListener(callbackContext);
        }

        if (!getNfcStatus().equals(STATUS_NFC_OK)) {
            callbackContext.error(getNfcStatus());
            return true; // short circuit
        }

        createPendingIntent();

        if (action.equals(REGISTER_DEFAULT_TAG)) {
            registerDefaultTag(callbackContext);

        } else if (action.equals(REMOVE_DEFAULT_TAG)) {
            removeDefaultTag(callbackContext);

        } else if (action.equals(ADD_RESULT_LISTENER)) {
            addResultListener(callbackContext);

        } else if (action.equals(REMOVE_RESULT_LISTENER)) {
            removeResultListener(callbackContext);

        } else if (action.equalsIgnoreCase(INIT)) {
            init(callbackContext);

        } else if (action.equalsIgnoreCase(ENABLED)) {
            // status is checked before every call
            // if code made it here, NFC is enabled
            callbackContext.success(STATUS_NFC_OK);

        } else {
            // invalid action
            return false;
        }

        return true;
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            String stateString = "UNKNOWN";
            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                        NfcAdapter.STATE_OFF);
                switch (state) {
                    case NfcAdapter.STATE_OFF:
                        stateString = "STATE_OFF";
                        break;
                    case NfcAdapter.STATE_TURNING_OFF:
                        stateString = "STATE_TURNING_OFF";
                        break;
                    case NfcAdapter.STATE_ON:
                        stateString = "STATE_ON";
                        break;
                    case NfcAdapter.STATE_TURNING_ON:
                        stateString = "STATE_TURNING_ON";
                        break;
                }
                fireAdapterChangedEvent(stateString);
            }
        }
    };

    private void addAdapterStateListener(CallbackContext callbackContext) {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        getActivity().registerReceiver(broadcastReceiver, filter);
    }

    private void removeAdapterStateListener(CallbackContext callbackContext) {
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    private String getNfcStatus() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfcAdapter == null) {
            return STATUS_NO_NFC;
        } else if (!nfcAdapter.isEnabled()) {
            return STATUS_NFC_DISABLED;
        } else {
            return STATUS_NFC_OK;
        }
    }

    private void registerDefaultTag(CallbackContext callbackContext) {
        addTagFilter();
        callbackContext.success();
    }

    private void removeDefaultTag(CallbackContext callbackContext) {
        removeTagFilter();
        callbackContext.success();
    }

    private void addResultListener(CallbackContext callbackContext) {
        //TODO: remember setting
        callbackContext.success();
    }

    private void removeResultListener(CallbackContext callbackContext) {
        //TODO: remember setting
        callbackContext.success();
    }

    private void init(CallbackContext callbackContext) {
        Log.d(TAG, "Enabling plugin " + getIntent());

        startNfc();
        if (!recycledIntent()) {
            parseMessage();
        }
        callbackContext.success();
    }

    private void showSettings(CallbackContext callbackContext) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
            getActivity().startActivity(intent);
        } else {
            Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
            getActivity().startActivity(intent);
        }
        callbackContext.success();
    }

    private void createPendingIntent() {
        if (pendingIntent == null) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, activity.getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pendingIntent = PendingIntent.getActivity(activity, 0, intent, 0);
        }
    }

    private void addTechList(String[] list) {
        this.addTechFilter();
        this.addToTechList(list);
    }

    private void removeTechList(String[] list) {
        this.removeTechFilter();
        this.removeFromTechList(list);
    }

    private void addTechFilter() {
        intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED));
    }

    private boolean removeTechFilter() {
        boolean removed = false;
        Iterator<IntentFilter> iter = intentFilters.iterator();
        while (iter.hasNext()) {
            IntentFilter intentFilter = iter.next();
            if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intentFilter.getAction(0))) {
                intentFilters.remove(intentFilter);
                removed = true;
            }
        }
        return removed;
    }

    private void addTagFilter() {
        intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED));
    }

    private boolean removeTagFilter() {
        boolean removed = false;
        Iterator<IntentFilter> iter = intentFilters.iterator();
        while (iter.hasNext()) {
            IntentFilter intentFilter = iter.next();
            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intentFilter.getAction(0))) {
                intentFilters.remove(intentFilter);
                removed = true;
            }
        }
        return removed;
    }

    private void startNfc() {
        createPendingIntent(); // onResume can call startNfc before execute

        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter != null && !getActivity().isFinishing()) {
                    try {
                        nfcAdapter.enableForegroundDispatch(getActivity(), getPendingIntent(), getIntentFilters(), getTechLists());

                    } catch (IllegalStateException e) {
                        // issue 110 - user exits app with home button while nfc is initializing
                        Log.w(TAG, "Illegal State Exception starting NFC. Assuming application is terminating.");
                    }

                }
            }
        });
    }

    private void stopNfc() {
        Log.d(TAG, "stopNfc");
        getActivity().runOnUiThread(new Runnable() {
            public void run() {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter != null) {
                    try {
                        nfcAdapter.disableForegroundDispatch(getActivity());
                    } catch (IllegalStateException e) {
                        // issue 125 - user exits app with back button while nfc
                        Log.w(TAG, "Illegal State Exception stopping NFC. Assuming application is terminating.");
                    }
                }
            }
        });
    }

    private void addToTechList(String[] techs) {
        techLists.add(techs);
    }

    private void removeFromTechList(String[] techs) {
        techLists.remove(techs);
    }

    private boolean removeIntentFilter(String mimeType) throws MalformedMimeTypeException {
        boolean removed = false;
        Iterator<IntentFilter> iter = intentFilters.iterator();
        while (iter.hasNext()) {
            IntentFilter intentFilter = iter.next();
            String mt = intentFilter.getDataType(0);
            if (mimeType.equals(mt)) {
                intentFilters.remove(intentFilter);
                removed = true;
            }
        }
        return removed;
    }

    private PendingIntent getPendingIntent() {
        return pendingIntent;
    }

    private IntentFilter[] getIntentFilters() {
        return intentFilters.toArray(new IntentFilter[intentFilters.size()]);
    }

    private String[][] getTechLists() {
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return techLists.toArray(new String[0][0]);
    }

    private void parseMessage() {
        final AsyncResultInterface delegate = this;
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "parseMessage " + getIntent());
                Intent intent = getIntent();
                String action = intent.getAction();
                Log.d(TAG, "action " + action);
                if (action == null) {
                    return;
                }

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                if (action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
                    for (String tagTech : tag.getTechList()) {
                        Log.d(TAG, tagTech);
                        //Fire tag event here
                    }
                }

                if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
                    fireTagEvent(tag);
                }

                if (tag != null)
                    (new ReadCardTask(delegate)).execute(new Tag[]{tag});

                setIntent(new Intent());
            }
        });
    }

    private void fireTagEvent(Tag tag) {
        String command = MessageFormat.format(javaScriptEventTemplate, TAG_DEFAULT, Util.tagToJSON(tag));
        Log.v(TAG, command);
        this.webView.sendJavascript(command);
    }

    private void fireAdapterChangedEvent(String state) {
        String command = MessageFormat.format(adapterStateEventTemplate, ADAPTER_STATE, state);
        Log.v(TAG, command);
        this.webView.sendJavascript(command);
    }

    private boolean recycledIntent() { // TODO this is a kludge, find real solution
        int flags = getIntent().getFlags();
        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
            Log.i(TAG, "Launched from history, killing recycled intent");
            setIntent(new Intent());
            return true;
        }
        return false;
    }

    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "onPause " + getIntent());
        super.onPause(multitasking);
        if (multitasking) {
            // nfc can't run in background
            stopNfc();
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        Log.d(TAG, "onResume " + getIntent());
        super.onResume(multitasking);
        startNfc();
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent " + intent);
        super.onNewIntent(intent);
        setIntent(intent);
        savedIntent = intent;
        parseMessage();
    }

    private Activity getActivity() {
        return this.cordova.getActivity();
    }

    private Intent getIntent() {
        return getActivity().getIntent();
    }

    private void setIntent(Intent intent) {
        getActivity().setIntent(intent);
    }

    String javaScriptEventTemplate =
            "var e = document.createEvent(''Events'');\n" +
                    "e.initEvent(''{0}'');\n" +
                    "e.tag = {1};\n" +
                    "document.dispatchEvent(e);";

    String resultErrorsTemplate =
            "var e = document.createEvent(''Events'');\n" +
                    "e.initEvent(''{0}'');\n" +
                    "e.errors = {1};\n" +
                    "document.dispatchEvent(e);";

    String resultEventTemplate =
            "var e = document.createEvent(''Events'');\n" +
                    "e.initEvent(''{0}'');\n" +
                    "e.data = {1};\n" +
                    "document.dispatchEvent(e);";
    String adapterStateEventTemplate =
            "var e = document.createEvent(''Events'');\n" +
                    "e.initEvent(''{0}'');\n" +
                    "e.state = \"{1}\";\n" +
                    "document.dispatchEvent(e);";

    @Override
    public void onReadFinished(Pair<ReadCardResult, Wallet> data) {
        Log.d(TAG, "onReadFinished called");

        String command = "";
        String errors = "";
        String wallet = "";

        if (isValidData(data)) {
            wallet = data.getValue1().toJSONString();
            command = MessageFormat.format(resultEventTemplate, READ_RESULT, wallet);
        } else {
            JSONObject errorObject = new JSONObject();
            try {
                errorObject.put("error", "could not read data from card");
                errors = errorObject.toString();
                command = MessageFormat.format(resultErrorsTemplate, READ_RESULT, errors);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        this.webView.sendJavascript(command);
    }

    private boolean isValidData(Pair<ReadCardResult, Wallet> data) {
        return data != null && data.getValue0() != null
                && (ReadCardResult.SUCCESS == data.getValue0()/* || ReadCardResult.OLD_STYLE_WALLET == data.getValue0()*/)
                && data.getValue1() != null;
    }
}
