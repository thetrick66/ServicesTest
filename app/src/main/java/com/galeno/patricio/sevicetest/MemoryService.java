package com.galeno.patricio.sevicetest;

/**
 * Created by Patricio on 31-08-2017.
 */

import android.app.ActivityManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.galeno.patricio.sevicetest.conexion.Server;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


/**
 * Un {@link Service} que notifica la cantidad de memoria disponible en el sistema
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MemoryService extends Service {

    private boolean disconnected = false;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    public static final String INTENT_EXTRA_SERVICE_ADDRESS = "BLE_SERVICE_DEVICE_ADDRESS";
    public static final String INTENT_EXTRA_SERVICE_NAME = "BLE_SERVICE_DEVICE_NAME";
    public static final String INTENT_EXTRA_SERVICE_DATA = "BLE_SERVICE_DATA";

    public final static String ACTION_BLE_REQ_ENABLE_BT = "com.microchip.mldpterminal3.ACTION_BLE_REQ_ENABLE_BT";
    public final static String ACTION_BLE_SCAN_RESULT = "com.microchip.mldpterminal3.ACTION_BLE_SCAN_RESULT";
    public final static String ACTION_BLE_CONNECTED = "com.microchip.mldpterminal3.ACTION_BLE_CONNECTED";
    public final static String ACTION_BLE_DISCONNECTED = "com.microchip.mldpterminal3.ACTION_BLE_DISCONNECTED";
    public final static String ACTION_BLE_DATA_RECEIVED = "com.microchip.mldpterminal3.ACTION_BLE_DATA_RECEIVED";

    //The MLDP UUID will be included in the RN4020 Advertising packet unless a private service and characteristic exists. In that case use the private service UUID here instead.
    private final static byte[] SCAN_RECORD_MLDP_PRIVATE_SERVICE = {0x00, 0x03, 0x00, 0x3a, 0x12, 0x08, 0x1a, 0x02, (byte) 0xdd, 0x07, (byte) 0xe6, 0x58, 0x03, 0x5b, 0x03, 0x00};

    private final static UUID UUID_MLDP_PRIVATE_SERVICE = UUID.fromString("00035b03-58e6-07dd-021a-08123a000300"); //Private service for Microchip MLDP
    private final static UUID UUID_MLDP_DATA_PRIVATE_CHAR = UUID.fromString("00035b03-58e6-07dd-021a-08123a000301"); //Characteristic for MLDP Data, properties - notify, write
    private final static UUID UUID_MLDP_CONTROL_PRIVATE_CHAR = UUID.fromString("00035b03-58e6-07dd-021a-08123a0003ff"); //Characteristic for MLDP Control, properties - read, write
    /*
    private final static UUID UUID_TANSPARENT_PRIVATE_SERVICE = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"); //Private service for Microchip Transparent
    private final static UUID UUID_TRANSPARENT_TX_PRIVATE_CHAR = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616"); //Characteristic for Transparent Data from BM module, properties - notify, write, write no response
    private final static UUID UUID_TRANSPARENT_RX_PRIVATE_CHAR = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"); //Characteristic for Transparent Data to BM module, properties - write, write no response
    */
    private final static UUID UUID_TANSPARENT_PRIVATE_SERVICE = UUID.fromString("66ecf52c-e19f-11e4-8a00-1681e6b88ec1"); //Private service for Microchip Transparent
    private final static UUID UUID_TRANSPARENT_TX_PRIVATE_CHAR = UUID.fromString("66ecf194-e19f-11e4-8a00-1681e6b88ec2"); //Characteristic for Transparent Data from BM module, properties - notify, write, write no response
    private final static UUID UUID_TRANSPARENT_RX_PRIVATE_CHAR = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"); //Characteristic for Transparent Data to BM module, properties - write, write no response

    private final static UUID UUID_CHAR_NOTIFICATION_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //Special descriptor needed to enable notifications

    private UUID[] uuidScanList = {UUID_MLDP_PRIVATE_SERVICE, UUID_TANSPARENT_PRIVATE_SERVICE};
    private final Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private final Queue<BluetoothGattCharacteristic> characteristicWriteQueue = new LinkedList<BluetoothGattCharacteristic>();
    private BluetoothGattCharacteristic mldpDataCharacteristic, transparentTxDataCharacteristic, transparentRxDataCharacteristic;
    private int connectionAttemptCountdown = 0;

    private static final String TAG = MemoryService.class.getSimpleName();
    TimerTask timerTask;
    private boolean change = false;
    private String output="";
    public MemoryService() {
    }

    /* Método de acceso */
    public class LocalBinder extends Binder {
        MemoryService getService() {
            return MemoryService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /*@Override
    public IBinder onBind(Intent intent) {
        return null;
    }*/


    @Override
    public void onCreate() {
        Log.d(TAG, "Servicio creado...");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Servicio iniciado...");

        /*
        final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        final ActivityManager activityManager =
                (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        */
        try {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);          //Get a reference to BluetoothManager from the operating system
            if (bluetoothManager == null) {                                                             //Check that we did get a BluetoothManager
                Log.e(TAG, "Unable to initialize the BluetoothManager");
            }
            else {
                bluetoothAdapter = bluetoothManager.getAdapter();                                       //Get a reference to BluetoothAdapter from the BluetoothManager
                if (bluetoothAdapter == null) {                                                         //Check that we did get a BluetoothAdapter
                    Log.e(TAG, "Unable to obtain a BluetoothAdapter");
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
        connect("00:1E:C0:3E:03:77");

        //Timer para la funcionalidad de reconectar de forma manual pero mas rapida, alternativa propia:
        //connectGatt(this, TRUE, bleGattCallback);
        Timer timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (disconnected) {
                    Log.d(TAG, "RECONECTAR");
                    connect("00:1E:C0:3E:03:77");
                }
                //activityManager.getMemoryInfo(memoryInfo);
                //String availMem = memoryInfo.availMem / 1048576 + "MB";

                //Log.d(TAG, availMem);

                //Intent localIntent = new Intent(Constants.ACTION_RUN_SERVICE)
                //        .putExtra(Constants.EXTRA_MEMORY, availMem);

                // Emitir el intent a la actividad
                //LocalBroadcastManager.
                //        getInstance(MemoryService.this).sendBroadcast(localIntent);
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 1000);



        //bluetoothGatt.setCharacteristicNotification(mldpDataCharacteristic, true);
        /*
        BluetoothGattDescriptor descriptor = mldpDataCharacteristic.getDescriptor(
                UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
        */
        //bluetoothGatt.setCharacteristicNotification(transparentTxDataCharacteristic, true);
        //bluetoothGatt.setCharacteristicNotification(transparentRxDataCharacteristic, true);
        /*
        Timer timer = new Timer();

        timerTask = new TimerTask() {
            @Override
            public void run() {
                AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {

                    @Override
                    protected void onPreExecute() {
                    }

                    @Override
                    protected String doInBackground(Void... params) {

                        //String resultado = new Server().connectToServer("http://telemarket.telprojects.xyz/?s", 15000);
                        String resultado="";
                        if (change){
                            resultado = "Uno";
                            change = false;
                        }
                        else{
                            change = true;
                            resultado = "Dos";
                        }
                        return resultado;
                    }

                    @Override
                    protected void onPostExecute(String resultado) {

                        if (resultado != null) {
                            //System.out.println(resultado);
                            Intent localIntent = new Intent(Constants.ACTION_RUN_SERVICE)
                                    .putExtra(Constants.EXTRA_MEMORY, resultado);

                            // Emitir el intent a la actividad
                            LocalBroadcastManager.
                                    getInstance(MemoryService.this).sendBroadcast(localIntent);
                            //Why god... why

                            //reposes = getFeedS(resultado);
                            //System.out.println(reposes);
                        }
                        else{
                            Intent localIntent = new Intent(Constants.ACTION_RUN_SERVICE)
                                    .putExtra(Constants.EXTRA_MEMORY, "Else");

                            // Emitir el intent a la actividad
                            LocalBroadcastManager.
                                    getInstance(MemoryService.this).sendBroadcast(localIntent);
                        }
                    }
                };

                task.execute();
            }
        };
        //timer.scheduleAtFixedRate(timerTask, 0, 1000);
        */
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        timerTask.cancel();
        Intent localIntent = new Intent(Constants.ACTION_MEMORY_EXIT);

        // Emitir el intent a la actividad
        LocalBroadcastManager.
                getInstance(MemoryService.this).sendBroadcast(localIntent);
        if (bluetoothGatt != null) {                                                                //See if there is an existing Bluetooth connection
            bluetoothGatt.close();                                                                  //Close the connection as the service is ending
            bluetoothGatt = null;                                                                   //Remove the reference to the connection we had
        }
        disconnect();
        Log.d(TAG, "Servicio destruido...");
    }

    // Connect to a Bluetooth LE device with a specific address
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean connect(final String address) {
        try {
            if (bluetoothAdapter == null || address == null) {
                Log.w(TAG, "BluetoothAdapter not initialized or unspecified address");
                return false;
            }
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            if (bluetoothDevice == null) {
                Log.w(TAG, "Unable to connect because device was not found");
                return false;
            }
            if (bluetoothGatt != null) {                                                                //See if an existing connection needs to be closed
                bluetoothGatt.close();                                                                  //Faster to create new connection than reconnect with existing BluetoothGatt
            }
            connectionAttemptCountdown = 3;                                                             //Try to connect three times for reliability
            bluetoothGatt = bluetoothDevice.connectGatt(this, false, bleGattCallback);                           //Directly connect to the device , so set autoConnect to false
            Log.d(TAG, "Attempting to create a new Bluetooth connection");
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Disconnect an existing connection or cancel a connection that has been requested
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void disconnect() {
        try {
            if (bluetoothAdapter == null || bluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            connectionAttemptCountdown = 0;                                                             //Stop counting connection attempts
            bluetoothGatt.disconnect();
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // Implements callback methods for GATT events such as connecting, discovering services, write completion, etc.
    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {
        //Connected or disconnected
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    connectionAttemptCountdown = 0;                                                     //Stop counting connection attempts
                    if (newState == BluetoothProfile.STATE_CONNECTED) {                                 //Connected
                        final Intent intent = new Intent(ACTION_BLE_CONNECTED);
                        sendBroadcast(intent);
                        disconnected = false;
                        Log.i(TAG, "Connected to BLE device");
                        descriptorWriteQueue.clear();                                                   //Clear write queues in case there was something left in the queue from the previous connection
                        characteristicWriteQueue.clear();
                        bluetoothGatt.discoverServices();                                               //Discover services after successful connection
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                         //Disconnected
                        final Intent intent = new Intent(ACTION_BLE_DISCONNECTED);
                        sendBroadcast(intent);
                        disconnected = true;
                        Log.i(TAG, "Disconnected from BLE device");
                    }
                }
                else {                                                                                  //Something went wrong with the connection or disconnection request
                    if (connectionAttemptCountdown-- > 0) {                                             //See is we should try another attempt at connecting
                        gatt.connect();                                                                 //Use the existing BluetoothGatt to try connect
                        Log.d(TAG, "Connection attempt failed, trying again");
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                         //Not trying another connection attempt and are not connected
                        final Intent intent = new Intent(ACTION_BLE_DISCONNECTED);
                        sendBroadcast(intent);
                        disconnected = true;
                        Log.i(TAG, "Unexpectedly disconnected from BLE device");
                    }
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Service discovery completed
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            try {
                mldpDataCharacteristic = transparentTxDataCharacteristic = transparentRxDataCharacteristic = null;
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    List<BluetoothGattService> gattServices = gatt.getServices();                       //Get the list of services discovered
                    if (gattServices == null) {
                        Log.d(TAG, "No BLE services found");
                        return;
                    }
                    UUID uuid;
                    for (BluetoothGattService gattService : gattServices) {                             //Loops through available GATT services
                        uuid = gattService.getUuid();                                                   //Get the UUID of the service
                        if (uuid.equals(UUID_MLDP_PRIVATE_SERVICE) || uuid.equals(UUID_TANSPARENT_PRIVATE_SERVICE)) { //See if it is the MLDP or Transparent private service UUID
                            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Loops through available characteristics
                                uuid = gattCharacteristic.getUuid();                                    //Get the UUID of the characteristic
                                if (uuid.equals(UUID_TRANSPARENT_TX_PRIVATE_CHAR)) {                    //See if it is the Transparent Tx data private characteristic UUID
                                    transparentTxDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                        bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables notification on the server
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                                            bluetoothGatt.writeDescriptor(descriptor);                 //Write the descriptor
                                        }
                                    }
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Transparent service Tx characteristics");
                                }
                                if (uuid.equals(UUID_TRANSPARENT_RX_PRIVATE_CHAR)) {                    //See if it is the Transparent Rx data private characteristic UUID
                                    transparentRxDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Transparent service Rx characteristics");
                                }

                                if (uuid.equals(UUID_MLDP_DATA_PRIVATE_CHAR)) {                         //See if it is the MLDP data private characteristic UUID
                                    mldpDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                        bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables notification on the server
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                                            bluetoothGatt.writeDescriptor(descriptor);                 //Write the descriptor
                                        }
                                    }
//Use Indicate for RN4020 module firmware prior to 1.20 (not recommended)
//                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) { //Only see if the characteristic has the Indicate property if it does not have the Notify property
//                                        bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification (and indication) in the BluetoothGatt
//                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables indication on the server
//                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); //Set the value of the descriptor to enable indication
//                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
//                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
//                                            bluetoothGatt.writeDescriptor(descriptor);                  //Write the descriptor
//                                        }
//                                    }
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
//Use Write With Response for RN4020 module firmware prior to 1.20 (not recommended)
//                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (acknowledged) property
//                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with acknowledge) in the BluetoothGatt
//                                    }
                                    Log.d(TAG, "Found MLDP service and characteristics");
                                }
                            }
                            break;
                        }
                    }
                    if(mldpDataCharacteristic == null && (transparentTxDataCharacteristic == null || transparentRxDataCharacteristic == null)) {
                        Log.d(TAG, "Did not find MLDP or Transparent service");
                    }
                }
                else {
                    Log.w(TAG, "Failed service discovery with status: " + status);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Received notification or indication with new value for a characteristic
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            try {

            /*
                if (UUID_MLDP_DATA_PRIVATE_CHAR.equals(characteristic.getUuid()) || UUID_TRANSPARENT_TX_PRIVATE_CHAR.equals(characteristic.getUuid())) {                     //See if it is the MLDP data characteristic
                    String dataValue = characteristic.getStringValue(0);                                //Get the data in string format
                    //byte[] dataValue = characteristic.getValue();                                     //Example of getting data in a byte array
                    Log.d(TAG, "New notification or indication");
                    final Intent intent = new Intent(ACTION_BLE_DATA_RECEIVED);                         //Create the intent to announce the new data
                    intent.putExtra(INTENT_EXTRA_SERVICE_DATA, dataValue);                              //Add the data to the intent
                    sendBroadcast(intent);                                                              //Broadcast the intent
                }
             */
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for(byte byteChar : data)
                        stringBuilder.append(String.format("%02X", byteChar));
                    output = stringBuilder.toString();
                    Intent localIntent = new Intent(Constants.ACTION_RUN_SERVICE)
                            .putExtra(Constants.EXTRA_MEMORY, output);

                    // Emitir el intent a la actividad
                    LocalBroadcastManager.
                            getInstance(MemoryService.this).sendBroadcast(localIntent);
                    AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {

                        @Override
                        protected void onPreExecute() {
                        }

                        @Override
                        protected String doInBackground(Void... params) {

                            //String resultado = new Server().connectToServer("http://telemarket.telprojects.xyz/?s", 15000);
                            String resultado = new Server().connectToServer("http://galenoproject.sytes.net/data/?name=ECG&data="+output+"&save=yes", 15000);
                            /*
                            if (change){
                                resultado = "Uno";
                                change = false;
                            }
                            else{
                                change = true;
                                resultado = "Dos";
                            }
                            */
                            return resultado;
                        }

                        @Override
                        protected void onPostExecute(String resultado) {

                            if (resultado != null) {
                                //System.out.println(resultado);
                                Intent localIntent = new Intent(Constants.ACTION_RUN_SERVICE)
                                        .putExtra(Constants.EXTRA_MEMORY, output);

                                // Emitir el intent a la actividad
                                LocalBroadcastManager.
                                        getInstance(MemoryService.this).sendBroadcast(localIntent);
                                //Why god... why

                                //reposes = getFeedS(resultado);
                                //System.out.println(reposes);
                            }
                            else{
                                Intent localIntent = new Intent(Constants.ACTION_RUN_SERVICE)
                                        .putExtra(Constants.EXTRA_MEMORY, "Else");

                                // Emitir el intent a la actividad
                                LocalBroadcastManager.
                                        getInstance(MemoryService.this).sendBroadcast(localIntent);
                            }
                        }
                    };

                    task.execute();
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Write completed
        //Use write queue because BluetoothGatt can only do one write at a time
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {                                             //See if the write was successful
                    Log.w(TAG, "Error writing GATT characteristic with status: " + status);
                }
                characteristicWriteQueue.remove();                                                      //Pop the item that we just finishing writing
                if(characteristicWriteQueue.size() > 0) {                                               //See if there is more to write
                    bluetoothGatt.writeCharacteristic(characteristicWriteQueue.element());              //Write characteristic
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Write descriptor completed
        //Use write queue because BluetoothGatt can only do one write at a time
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Error writing GATT descriptor with status: " + status);
                }
                descriptorWriteQueue.remove();                                                          //Pop the item that we just finishing writing
                if(descriptorWriteQueue.size() > 0) {                                                   //See if there is more to write
                    bluetoothGatt.writeDescriptor(descriptorWriteQueue.element());                      //Write descriptor
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Read completed. For information only. This application uses Notification or Indication to receive updated characteristic data, not Read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        }
    };

}