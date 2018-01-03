package com.carsense.usbstringreader;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Created by apple on 02/01/18.
 */

public class Constants {
    public static boolean IS_USB_CONNECTED = false;
    public static int BAUD_RATE_FOR_USB_READING = 57600;
    public static String KEY_FOR_LOG_STRINGS_INTENT = "logStrings";

    public static final String ROOT_FOLDER_NAME = "UsbLogger";
    public static final String ROOT_PATH_FOR_SAVING_TRIP_FOLDERS = Environment.getExternalStorageDirectory() + File.separator + ROOT_FOLDER_NAME;
    public static final String DEFAULT_FILE_NAME_FOR_RAW_USB_DATA = "rawUsbData";
    public static String TRIP_FOLDER_NAME = "";

    public static File usbFile = null;
    public static OutputStreamWriter usbOutputStreamWriter = null;
    public static FileOutputStream usbFos = null;

    public static int MAX_NUMBER_OF_BYTES = 20;

    public static boolean IS_DOLLAR_STAR_CONDITION = false;

    public static char START_CHAR = '$';
    public static char END_CHAR = '*';

}
