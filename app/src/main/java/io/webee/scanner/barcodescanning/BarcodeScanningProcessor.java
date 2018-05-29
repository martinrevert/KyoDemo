package io.webee.scanner.barcodescanning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.List;

import io.webee.scanner.FrameMetadata;
import io.webee.scanner.GraphicOverlay;
import io.webee.scanner.R;
import io.webee.scanner.VisionProcessorBase;


public class BarcodeScanningProcessor extends VisionProcessorBase<List<FirebaseVisionBarcode>> {

    private static final String TAG = "BarcodeScanProc";

    private final FirebaseVisionBarcodeDetector detector;
    private static onReadBarCodeListener onReadBarCodeListener;
    private MediaPlayer mp;
    private Context context;

    public interface onReadBarCodeListener {
        public void onReadBarCode(JsonObject eventLog);
    }

    public static void subscribeToListener(onReadBarCodeListener listener) {
        onReadBarCodeListener = listener;
    }

    public static void unSubscribeToListener() {
        onReadBarCodeListener = null;
    }


    public BarcodeScanningProcessor(Context context) {
        // Note that if you know which format of barcode your app is dealing with, detection will be
        // faster to specify the supported barcode formats one by one, e.g.
        // new FirebaseVisionBarcodeDetectorOptions.Builder()
        //     .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE)
        //     .build();
        detector = FirebaseVision.getInstance().getVisionBarcodeDetector();
        this.context = context;
    }

    @Override
    public void stop() {
        try {
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

            //ToDo MediaPlyer sonido de scaneo
            mp = MediaPlayer.create(context, R.raw.fuzzybeep);
            mp.start();

            Log.v("BarcodeScanningProcessor", "Scan succesful");

            JsonObject message = new JsonObject();
            try {
                message.addProperty("deviceId", "1122334455668899");
                message.addProperty("deviceName", "SCANNER");
                //En devices ficticios no es necesario enviar protocolo
                //message.addProperty("protocol", "zigbee");
                message.addProperty("barcode", barcode.getDisplayValue());
                message.addProperty("user", "Martin");

                //message.addProperty("deviceId", "0011223344556677");
                //message.addProperty("deviceName", "ASHRAE");
                //message.addProperty("quality",80);
            } catch (JsonIOException e) {
                e.printStackTrace();
            }

            if (onReadBarCodeListener != null)
                onReadBarCodeListener.onReadBarCode(message);
        }
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Barcode detection failed " + e);
    }

}
