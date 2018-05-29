package io.webee.scanner.socketcomm;

import android.util.Log;

import com.google.gson.Gson;

import java.net.URISyntaxException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.webee.scanner.constants.Constants;
import io.webee.scanner.model.VioTMessage;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class SocketVioTManager {

/*    private static SocketVioTManager single_instance;
    private static CompositeDisposable mCompositeDisposable;
  //  private Socket mSocket;



    public static SocketVioTManager getInstance() {
        mCompositeDisposable = new CompositeDisposable();
        if (single_instance == null)
            single_instance = new SocketVioTManager();

        return single_instance;
    }

    public void processMessage(String barcode) {

        // if (mSocket == null) {
        create();
        // }

        VioTMessage message = new VioTMessage();
        Gson gson = new Gson();

        message.setBarcode(barcode);
        message.setDeviceId("0011223344558899");
        message.setProtocol("zigbee");
        //ToDo get user from SharedPreferences
        message.setUser("Martin");
        message.setDeviceName("SCANNER");

        gson.toJson(message);

        mSocket.emit("webee-hub-logger", gson);
        Log.v("SocketViotManager", "Message sent");

        disconnect();
    }

    private void create() {


        try {
            mSocket = IO.socket(Constants.VOIT_BASE_URL, opts);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        Log.v("SocketViotManager", "Socket create");
        connect();
    }


    private void connect() {
        Log.v("SocketViotManager", "Socket connect");
        //Todo consultar implementación de IOConnectorVIoT.connect();
        mSocket.connect();

    }

    private void disconnect() {
        Log.v("SocketViotManager", "Socket disconnect");
        //ToDo desconectar socket? cuando?
        mSocket.disconnect();

    }

    private void getCredentials() {

        VioTCredentialsInterface vioTCredentialsInterface = new Retrofit.Builder()
                .baseUrl(Constants.VOIT_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(VioTCredentialsInterface.class);

        mCompositeDisposable.add(vioTCredentialsInterface.getCredentials(Constants.API_KEY, Constants.API_SECRET)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe( credentials->{}, Throwable));



    }

    private void handleResponse(){

    }

*/


}
