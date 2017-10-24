package com.realsil.ota;

import android.app.Application;


/**
 * Created by rain1_wen on 2015/12/18.
 */
public class OtaApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // initial Global Gatt
        GlobalGatt.initial(this);
        //test
    }
}
