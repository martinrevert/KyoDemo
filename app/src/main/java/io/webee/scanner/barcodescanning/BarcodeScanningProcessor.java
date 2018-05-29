package io.webee.scanner.barcodescanning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.google.nodejsmanager.nodejsmanager.ConnectionManager;
import com.example.google.nodejsmanager.nodejsmanager.SocketManager;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;
import io.webee.scanner.FrameMetadata;
import io.webee.scanner.GraphicOverlay;
import io.webee.scanner.VisionProcessorBase;
import io.webee.scanner.constants.Constants;


public class BarcodeScanningProcessor extends VisionProcessorBase<List<FirebaseVisionBarcode>> implements ConnectionManager.EventCallbackListener {

    private static final String TAG = "BarcodeScanProc";

    private final FirebaseVisionBarcodeDetector detector;
    private final Context context;
    private boolean authenticated = false;
    private Timer schedulePing;
    private Emitter.Listener onAuthenticated = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            authenticated = true;
            Log.d(TAG, "VIoT - onAuthenticated");
            socketManager.getSocket().emit("webee-hub-logger", "Hola mundo");
//            socketManager.getSocket().emit("lb-ping");
//            if (schedulePing == null) {
//                schedulePing = newScheduledThreadPool(5);
//            }
//            schedulePing.scheduleAtFixedRate(new Runnable() {
//                public void run() {
//                    try {
//                        if ( socketManager.getSocket() != null
//                                &&  socketManager.getSocket().connected()) {
//                            socketManager.getSocket().emit("lb-ping");
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            }, 0, 15, TimeUnit.SECONDS);
        }
    };

    private SocketManager socketManager;

    public BarcodeScanningProcessor(Context context) {
        // Note that if you know which format of barcode your app is dealing with, detection will be
        // faster to specify the supported barcode formats one by one, e.g.
        // new FirebaseVisionBarcodeDetectorOptions.Builder()
        //     .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE)
        //     .build();

        this.context = context;
        ConnectionManager.subscribeToListener(this);
        detector = FirebaseVision.getInstance().getVisionBarcodeDetector();

        socketManager = new SocketManager(context);
        IO.Options opts = new IO.Options();
        opts.transports = new String[]{WebSocket.NAME};
        socketManager.createSocket(Constants.VIOT_BASE_URL_ALTERNATIVE, opts);


        socketManager.getSocket().on("authenticated", onAuthenticated);


        if (socketManager.getSocket().connected()) {
            socketManager.getSocket().disconnect();
        }
        socketManager.getSocket().connect();


    }

    @Override
    public void stop() {
        try {
            ConnectionManager.unSubscribeToListener();
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Barcode Detector: " + e);
        }
    }

    @Override
    protected Task<List<FirebaseVisionBarcode>> detectInImage(FirebaseVisionImage image) {
        return detector.detectInImage(image);
    }

    @SuppressLint("LongLogTag")
    @Override
    protected void onSuccess(
            @NonNull List<FirebaseVisionBarcode> barcodes,
            @NonNull FrameMetadata frameMetadata,
            @NonNull GraphicOverlay graphicOverlay) {
        graphicOverlay.clear();
        for (int i = 0; i < barcodes.size(); ++i) {
            FirebaseVisionBarcode barcode = barcodes.get(i);
            BarcodeGraphic barcodeGraphic = new BarcodeGraphic(graphicOverlay, barcode);
            graphicOverlay.add(barcodeGraphic);

            //ToDo Here we have to call to VIOT and we have to notify the Activity to start a WebView

            Log.v("BarcodeScanningProcessor", "Scan succesful");


//            if (!authenticated) {
//                Log.v("BarcodeScanningProcessor", "no athennticatred");
//                return;
//            }
//            JSONObject message = new JSONObject();
//            try {
//                message.put("deviceId", "0011223344558899");
//                message.put("deviceName", "SCANNER");
//                message.put("protocol", "zigbee");
//                message.put("barcode", barcode.getDisplayValue());
//                message.put("user", "Martin");
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//            socketManager.getSocket().emit("webee-hub-logger", "Hola mundo");
//            socketManager.getSocket().emit("webee-hub-logger", message);

        }
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Barcode detection failed " + e);
    }

    static JSONObject getCredentials() {
        try {
            String secondPart = "/api/connections/generateToken?api_key=%s&api_secret=%s";
            String[] APIs = new String[]{Constants.API_KEY, Constants.API_SECRET};
            String generateTokenApi = Constants.VIOT_BASE_URL_ALTERNATIVE + secondPart;
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
                            Log.i(TAG, "json: "+requestJSONObject);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                }

                break;
            }
            case ConnectionManager.EVENT_DISCONNECT:{
                Log.d(TAG, "VIoT - onDisconnectEvent");
                break;
            }
        }
    }

    public void emitMessage() {
        socketManager.getSocket().emit("webee-hub-logger", "Tonchi se come la banana");
    }
}
