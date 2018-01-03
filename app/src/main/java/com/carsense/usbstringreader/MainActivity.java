package com.carsense.usbstringreader;

import android.Manifest;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.carsense.usbstringreader.Interfaces.UpdatesCallBack;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public final static int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 101;

    Button btnStartLogging;
    Button btnEndLogging;

    TextView logView;
    String logText = "";
    int i =0;
    ScrollView scrollNow;

    private static UpdatesCallBack updateListenerForUsb = null;

    ArrayList<String> allStrings = new ArrayList<>();
    public static String prevString = "";

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

        btnStartLogging = (Button) findViewById(R.id.start_logging);
        btnStartLogging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnStartLogging.setVisibility(View.INVISIBLE);
                btnEndLogging.setVisibility(View.VISIBLE);
                requestPermissions();
                String pathForFiles = createFilePath();
                try {

                    //usb data
                    Constants.usbFile = new File(pathForFiles, Constants.DEFAULT_FILE_NAME_FOR_RAW_USB_DATA + ".txt");
                    Constants.usbFos = new FileOutputStream(Constants.usbFile);
                    Constants.usbOutputStreamWriter = new OutputStreamWriter(Constants.usbFos);


                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        });
        btnEndLogging = (Button) findViewById(R.id.end_logging);
        btnEndLogging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnStartLogging.setVisibility(View.VISIBLE);
                btnEndLogging.setVisibility(View.INVISIBLE);
                try {
                    Constants.usbOutputStreamWriter.close();
                    Constants.usbFos.close();
                    Constants.usbFile = null;
                } catch (Exception e) {

                }

            }
        });

        scrollNow = (ScrollView) findViewById(R.id.scroll_view);
        logView = (TextView) findViewById(R.id.logs_view);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter);
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
     * onCreateOptionsMenu
     * @param menu
     * @return
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_start_screen, menu);
        return true;
    }

    /**
     * onOptionsItemSelected
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i1;
        switch (item.getItemId()) {

            case R.id.settings:
                i1 = new Intent(this, SettingsLogger.class);
                startActivity(i1);
                break;

        }
        return super.onOptionsItemSelected(item);
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
     * Parsing data as per n bytes at a time
     */
    public void parsingNBytestATime(String data){
        int numOfBytes = 0;
        int flag = 0;
        String finalOne = "";
        for (char c : data.toCharArray()) {
            if (numOfBytes != Constants.MAX_NUMBER_OF_BYTES) {
                finalOne += c;
                numOfBytes++;
            } else {
                numOfBytes = 0;
                prevString += finalOne;
                if (updateListenerForUsb != null) {
                    updateListenerForUsb.updateUsbData(prevString);
                }
                Log.e("Message",prevString);
                allStrings.add(prevString);
                prevString = "";
                finalOne = "";
            }
            flag = 1;
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

                // Get extra data included in the Intent
                if (true) {
                    if (logText.length() >= 65535) {
                        i =0;
                        logText =  str;
                    } else {
                        logText +=  str + "\n";
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //stuff that updates ui
                        logView.setText(logText);
                        scrollNow.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });

                if (Constants.usbOutputStreamWriter != null) {
                    Constants.usbOutputStreamWriter.write(Calendar.getInstance().getTime() + " , " + str + "\n");
                } else {
                    Log.e("TAFG", "Unable to write");
                }

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
            if (Constants.IS_DOLLAR_STAR_CONDITION) {
                processStringData(data);
            } else {
                parsingNBytestATime(data);
            }
            Log.e("ProcessData",data);
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

    public String createFilePath() {
        File createRootFolder = new File(Environment.getExternalStorageDirectory(), Constants.ROOT_FOLDER_NAME);

        if (!createRootFolder.exists()) {
            createRootFolder.mkdirs();
        }

        Calendar cal = Calendar.getInstance();
        Constants.TRIP_FOLDER_NAME = cal.get(Calendar.DATE) + "z" + (cal.get(Calendar.MONTH) + 1) + "z" + cal.get(Calendar.HOUR_OF_DAY)
                + "z" + cal.get(Calendar.MINUTE) + "z" + cal.get(Calendar.SECOND);
        File createTripFolder = new File(Constants.ROOT_PATH_FOR_SAVING_TRIP_FOLDERS, Constants.TRIP_FOLDER_NAME);

        if (!createTripFolder.exists()) {
            createTripFolder.mkdirs();
        }

        String PATH_FOR_SAVING_FILES = Constants.ROOT_PATH_FOR_SAVING_TRIP_FOLDERS + File.separator
                + Constants.TRIP_FOLDER_NAME;
        return PATH_FOR_SAVING_FILES;
    }

    public void requestPermissions() {
        // Assume thisActivity is the current activity
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {


                } else {
                    Toast.makeText(this, "App May not work properly. Restart the app to grant permission.", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}
