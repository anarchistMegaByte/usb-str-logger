package com.carsense.usbstringreader;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.carsense.usbstringreader.Interfaces.UpdatesCallBack;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static UpdatesCallBack updateListenerForUsb = null;

    ArrayList<String> allStrings = new ArrayList<>();
    String prevString = "";

    UsbDevice usbDevice = null;
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    PendingIntent permissionIntent;
    UsbDeviceConnection usbConnection;
    UsbSerialDevice usbSerialport;
    AlertDialog usbDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        startUSBSending();
    }

    /**
     * onResumeView
     */
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");

        IntentFilter filterDetached = new IntentFilter();
        filterDetached.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");

        registerReceiver(recieverDetached, filterDetached);
        registerReceiver(receiver, filter);
        setUsbUpdateListener(actualUsbDataAfterParsing);

    }

    /**
     * onDestroyView
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        unregisterReceiver(recieverDetached);
        unregisterReceiver(usbPermissionReceiver);

        if (usbDialog != null) {
            if (usbDialog.isShowing()) {
                usbDialog.dismiss();
            }
            usbDialog.dismiss();
        }
    }


    //extra supporting classes

    /*
    Class for checking weather app is running in back ground or not.
     */
    class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Context... params) {
            final Context context = params[0].getApplicationContext();
            return isAppOnForeground(context);
        }

        private boolean isAppOnForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null) {
                return false;
            }
            final String packageName = context.getPackageName();
            for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static void setUsbUpdateListener(UpdatesCallBack mListener) {
        updateListenerForUsb = mListener;
    }

    /**
     * Initializing USB for recieveing Data
     */
    public void startUSBSending() {
        if (Constants.IS_USB_CONNECTED == false) {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
            if (!usbDevices.isEmpty()) {
                Log.e("TESTING","Check 1");
                boolean keep = true;
                for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                    usbDevice = entry.getValue();
                    int deviceVID = usbDevice.getVendorId();
                    int devicePID = usbDevice.getProductId();
                    if (deviceVID != 0x1d6b || (devicePID != 0x0001 || devicePID != 0x0002 || devicePID != 0x0003)) {
                        // We are supposing here there is only one device connected and it is our serial device
                        keep = false;
                    } else {
                        usbConnection = null;
                        usbDevice = null;
                    }
                    if (!keep)
                        break;
                }
            }

            if (usbDevice != null) {
                if (usbManager.hasPermission(usbDevice)) {
                    usbConnection = usbManager.openDevice(usbDevice);
                    usbSerialport = UsbSerialDevice.createUsbSerialDevice(usbDevice, usbConnection);
                    if (usbSerialport != null) {
                        if (usbSerialport.open()) {
                            // Devices are opened with default values, Usually 9600,8,1,None,OFF
                            // CDC driver default values 115200,8,1,None,OFF
                            usbSerialport.setBaudRate(Constants.BAUD_RATE_FOR_USB_READING);
                            usbSerialport.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            usbSerialport.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            usbSerialport.setParity(UsbSerialInterface.PARITY_NONE);
                            usbSerialport.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            //register usb update callback
                            setUsbUpdateListener(actualUsbDataAfterParsing);
                            Constants.IS_USB_CONNECTED = true;
                            usbSerialport.read(mCallback);
                        } else {
                            // Serial port could not be opened, maybe an I/O error or it CDC driver was chosen it does not really fit
                            Log.e("[Text]", "Serial port could not be opened");
                        }
                    } else {
                        // No driver for given device, even generic CDC driver could not be loaded
                        Log.e("[Text]", "No driver for given device");
                    }
                } else {
                    Log.e("Monday",""+"inside Oncreate called once" + "   08");
                    usbManager.requestPermission(usbDevice, permissionIntent);
                }
            }

        } else {
            setUsbUpdateListener(actualUsbDataAfterParsing);
            Toast.makeText(this, "USB already connected", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Parsing the data recieved from USB
     * @param data
     */
    public void processStringData(String data) {
        //Log.d("DATA INPUT",data);
        int flag = 0;
        String finalOne = "";
        for (char c : data.toCharArray()) {
            flag = 1;
            if (c != '$' && c != '*') {
                finalOne += c;
            }
            if (c == '$') {
                if (prevString.equals("")) {
                    finalOne = "";
                } else {
                    finalOne = "";
                }
            }
            if (c == '*') {
                prevString += finalOne;
                if (updateListenerForUsb != null) {
                    updateListenerForUsb.updateUsbData(prevString);
                }
                Log.e("Message",prevString);
                allStrings.add(prevString);
                prevString = "";
                finalOne = "";
            }
        }
        if (flag == 1) {
            prevString += finalOne;
        }
    }

    /**
     * Callback is called when parsing and validation of streaming data is Over
     */
    private UpdatesCallBack actualUsbDataAfterParsing = new UpdatesCallBack() {
        @Override
        public void updateUsbData(String str) {
            super.updateUsbData(str);
            try {
                Intent logIntent = new Intent("LOG_STRINGS");
                logIntent.putExtra(Constants.KEY_FOR_LOG_STRINGS_INTENT, str);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(logIntent);
                String[] commaSeparatedValues = str.split(",");

                Log.e("TSG", commaSeparatedValues.toString());
//                if (Constants.usbOutputStreamWriter != null) {
//                    Constants.usbOutputStreamWriter.write(Calendar.getInstance().getTimeInMillis() + "," + str + "\n");
//                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            data = new String(arg0);
            processStringData(data);
            //parseUSBLocationData(data);

        }
    };


    //Broadcast recievers

    /**
     * Reciever is called when USB device is Connected by OTG
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //do something based on the intent's action
            Log.e("USBMessages", "USB Connected");
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            if (deviceList.isEmpty()) {

            } else {
                for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
                    usbDevice = entry.getValue();
                    Log.e("UsbMessages",usbDevice.getDeviceName());
                    Log.e("UsbMessages",usbDevice.getProductId() + "");
                    Log.e("UsbMessages",usbDevice.getVendorId() + "");
                    Log.e("UsbMessages","---------------------------");
                    manager.requestPermission(usbDevice,permissionIntent );
                }
            }
        }
    };

    /**
     * Reciever when user grants/rejects permission
     */
    private BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int isSetGreen = 0;
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        //Onpressing Okay we end up here
                        if(device != null){
                            //call method to set up device communication
                            Log.d("UsbMessages", "permission accepted for device " + device);
                            usbConnection = manager.openDevice(device);
                            usbSerialport = UsbSerialDevice.createUsbSerialDevice(device,usbConnection);
                            if (usbSerialport != null) {
                                if (usbSerialport.open()) {
                                    // Devices are opened with default values, Usually 9600,8,1,None,OFF
                                    // CDC driver default values 115200,8,1,None,OFF
                                    usbSerialport.setBaudRate(Constants.BAUD_RATE_FOR_USB_READING);
                                    usbSerialport.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                    usbSerialport.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                    usbSerialport.setParity(UsbSerialInterface.PARITY_NONE);
                                    usbSerialport.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

                                    //register usb update callback
                                    //setUsbUpdateListener(actualUsbDataAfterParsing);
                                    Constants.IS_USB_CONNECTED = true;
                                    usbSerialport.read(mCallback);

                                    //SET Connection signal to usb
                                    isSetGreen = 1;

                                    if (usbDialog != null) {
                                        usbDialog.cancel();
                                    }
                                } else {
                                    // Serial port could not be opened, maybe an I/O error or it CDC driver was chosen it does not really fit
                                    Log.e("[Text]", "Serial port could not be opened");
                                }
                            }
                        }
                    } else {
                        //On pressing cancel we end up here
                        Log.d("UsbMessages", "permission denied for device " + device);
                    }
                }
            } else {
                Constants.IS_USB_CONNECTED = false;
            }

        }
    };

    /**
     * Called when device is detached. Do all the clean up process here.
     */
    private BroadcastReceiver recieverDetached = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            //do something based on the intent's action
            Log.e("USBMessages", "USB Disconnected");
            Constants.IS_USB_CONNECTED = false;
            if (usbConnection != null ) {
                usbConnection.close();
                usbSerialport.close();
            }

            boolean foregroud;
            try {
                // Use like this:
                foregroud = new ForegroundCheckTask().execute(context).get();
            } catch (Exception e) {
                foregroud = false;
                e.printStackTrace();
            }
            if (foregroud) {
                Toast.makeText(context,"Usb Disconnected",Toast.LENGTH_SHORT).show();
                usbDialog = new AlertDialog.Builder(context).create();
                usbDialog.setTitle("Warning: USB Removed !!");
                usbDialog.setMessage("00:10");
                usbDialog.setCancelable(false);

                usbDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Okay", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(context, "Okay Clicked",Toast.LENGTH_SHORT).show();

                    }
                });
                usbDialog.show();

                new CountDownTimer(30000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        usbDialog.setMessage( "Reconnect the usb in " + "00:"+ (millisUntilFinished/1000) + " secs to continue !!");
                    }

                    @Override
                    public void onFinish() {
                        if (usbDialog.isShowing())
                            usbDialog.dismiss();
                    }
                }.start();
            }

        }
    };

}
