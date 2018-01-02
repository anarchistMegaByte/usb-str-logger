package com.carsense.usbstringreader.Interfaces;

import android.util.Log;

/**
 * Created by carnot on 20/04/17.
 */

public abstract class UpdatesCallBack implements UpdatesInterface {

    @Override
    public void updateUsbData(String str) {
        Log.d("UpdatesCallBack","Into Abstract class");
    }

}
