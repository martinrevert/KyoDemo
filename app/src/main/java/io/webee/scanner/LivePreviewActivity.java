package io.webee.scanner;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;


import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;


import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.google.nodejsmanager.nodejsmanager.ConnectionManager;
import com.example.google.nodejsmanager.nodejsmanager.SocketManager;
import com.google.android.gms.common.annotation.KeepName;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;
import io.webee.scanner.barcodescanning.BarcodeScanningProcessor;
import io.webee.scanner.constants.Constants;

import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;

import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.SslErrorHandler;


import static android.provider.ContactsContract.Directory.PACKAGE_NAME;
import static java.util.concurrent.Executors.newScheduledThreadPool;


@KeepName
public final class LivePreviewActivity extends AppCompatActivity
        implements OnRequestPermissionsResultCallback,
        OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener, ConnectionManager.EventCallbackListener, BarcodeScanningProcessor.onReadBarCodeListener {
    private static final String BARCODE_DETECTION = "Barcode Detection";
    private static final String TAG = "LivePreviewActivity";
    private static final int PERMISSION_REQUESTS = 1;

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private String selectedModel = BARCODE_DETECTION;
    private static final String EXTRA_CUSTOM_TABS_TOOLBAR_COLOR = "tabColor";
    private WebView webView;
    private CustomTabsClient mClient;
    private String url;


    private Context context;
    private BarcodeScanningProcessor barcodeScanningProcessor;
    private int connectionIntents = 0;

    private ScheduledExecutorService schedulePingViot;
    private Emitter.Listener onAuthenticated = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(TAG, "VIoT - onAuthenticated");
            socketManager.getSocket().emit("lb-ping");
            if (schedulePingViot == null) {
                schedulePingViot = newScheduledThreadPool(5);
            }
            schedulePingViot.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    try {
                        if (socketManager.getSocket() != null
                                && socketManager.getSocket().connected()) {
                            socketManager.getSocket().emit("lb-ping");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 15, TimeUnit.SECONDS);
        }
    };
    private Emitter.Listener onAndroidPongVIOT = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                Log.d(TAG, "VIoT - pong received ...");
            }
        }
    };

    private SocketManager socketManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        context = this;

        setContentView(R.layout.activity_live_preview);

        preview = (CameraSourcePreview) findViewById(R.id.firePreview);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = (GraphicOverlay) findViewById(R.id.fireFaceOverlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }
        webView = (WebView) findViewById(R.id.webview);
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        List<String> options = new ArrayList<>();

        options.add(BARCODE_DETECTION);


        // Creating adapter for spinner for future features (NFC, BLE Beacon, etc)
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // attaching data adapter to spinner
        spinner.setAdapter(dataAdapter);
        spinner.setOnItemSelectedListener(this);

        ToggleButton facingSwitch = (ToggleButton) findViewById(R.id.facingswitch);
        facingSwitch.setOnCheckedChangeListener(this);

        if (allPermissionsGranted()) {
            try {
                createCameraSource(selectedModel);
            } catch (FirebaseMLException e) {
                e.printStackTrace();
            }
        } else {
            getRuntimePermissions();
        }

        connectSocketViot();


        url = "https://visual.webee.io/apps/view/5b0c45176e06475826e022a6/3bd115f0-d9fc-40e9-bb48-93bfcc6cdaeb";

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            warmUpChrome();
            launchUrl();
        } else {
            setupWebView();
        }
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setPluginState(WebSettings.PluginState.ON);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setSaveFormData(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

        });
        webView.loadUrl(url);
    }

    private void warmUpChrome() {
        CustomTabsServiceConnection service = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
                mClient = client;
                mClient.warmup(0);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mClient = null;
            }
        };

        CustomTabsClient.bindCustomTabsService(getApplicationContext(), PACKAGE_NAME, service);
    }

    private void launchUrl() {
        Uri uri = Uri.parse(url);
        if (uri == null) {
            return;
        }
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().
                //setToolbarColor(getResources().getColor(R.color.control_background)).
                        setShowTitle(true).build();
        customTabsIntent.intent.setData(uri);

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(customTabsIntent.intent, PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo resolveInfo : resolveInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (TextUtils.equals(packageName, PACKAGE_NAME))
                customTabsIntent.intent.setPackage(PACKAGE_NAME);
        }

        customTabsIntent.launchUrl(this, uri);
    }


    private void connectSocketViot() {
        if (connectionIntents > 3) {
            showErrorMessage();
            finish();
            return;
        }
        connectionIntents++;
        socketManager = new SocketManager(context);
        IO.Options opts = new IO.Options();
        opts.transports = new String[]{WebSocket.NAME};
        //opts.forceNew = true;
        socketManager.createSocket(Constants.VIOT_BASE_URL, opts);


        socketManager.getSocket().on("authenticated", onAuthenticated);
        socketManager.getSocket().on("lb-pong", onAndroidPongVIOT);


        if (socketManager.getSocket().connected()) {
            socketManager.getSocket().disconnect();
        }
        socketManager.getSocket().connect();
    }

    private void showErrorMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "Socket disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public synchronized void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        selectedModel = parent.getItemAtPosition(pos).toString();
        Log.d(TAG, "Selected model: " + selectedModel);
        preview.stop();
        if (allPermissionsGranted()) {
            try {
                createCameraSource(selectedModel);
            } catch (FirebaseMLException e) {
                e.printStackTrace();
            }
            startCameraSource();
        } else {
            getRuntimePermissions();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Set facing");
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            } else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }
        }
        preview.stop();
        startCameraSource();
    }

    private void createCameraSource(String model) throws FirebaseMLException {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }

        switch (model) {
            case BARCODE_DETECTION:
                Log.i(TAG, "Using Barcode Detector Processor");
                barcodeScanningProcessor = new BarcodeScanningProcessor(this);
                cameraSource.setMachineLearningFrameProcessor(barcodeScanningProcessor);
                break;

            default:
                Log.e(TAG, "Unknown model: " + model);
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
        BarcodeScanningProcessor.subscribeToListener(this);
        ConnectionManager.subscribeToListener(this);
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        BarcodeScanningProcessor.unSubscribeToListener();
        ConnectionManager.unSubscribeToListener();
        preview.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (socketManager.getSocket() != null)
            socketManager.getSocket().disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (allPermissionsGranted()) {
            try {
                createCameraSource(selectedModel);
            } catch (FirebaseMLException e) {
                e.printStackTrace();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

    static JSONObject getCredentials() {
        try {
            String secondPart = "/api/connections/generateToken?api_key=%s&api_secret=%s";
            String[] APIs = new String[]{Constants.API_KEY, Constants.API_SECRET};
            String generateTokenApi = Constants.VIOT_BASE_URL + secondPart;
            URL url = new URL(String.format(generateTokenApi, APIs[0],
                    APIs[1]));
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            StringBuilder json = new StringBuilder(1024);
            String tmp;
            while ((tmp = reader.readLine()) != null)
                json.append(tmp).append("\n");
            reader.close();
            return new JSONObject(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onEventCallbackReceived(String event, String socketIdentifier) {
        switch (event) {
            case ConnectionManager.EVENT_CONNECT: {
                Log.d(TAG, "VIoT - onConnectEvent");
                if (socketManager.getSocket() != null) {
                    JSONObject json = getCredentials();
                    try {
                        if (json != null) {
                            JSONObject requestJSONObject = new JSONObject();
                            requestJSONObject.put("id", json.getString("id"));
                            requestJSONObject.put("connectionId", json.getString("connectionId"));
                            requestJSONObject.put("agent", "hub");
                            requestJSONObject.put("uuid", "ACDBDA37565C");
                            socketManager.getSocket().emit("webee-auth-strategy", requestJSONObject);
                            Log.i(TAG, "json: " + requestJSONObject);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case ConnectionManager.EVENT_DISCONNECT: {
                Log.d(TAG, "VIoT - onDisconnectEvent");
                connectSocketViot();
                break;
            }
        }
    }

    @Override
    public void onReadBarCode(JsonObject message) {
        Log.v(TAG, "message" + message);
        socketManager.getSocket().emit("webee-hub-logger", message);
    }
}
