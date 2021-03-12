package com.esplibrary.bluetooth;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.JsonReader;
import android.util.Log;

import androidx.annotation.Nullable;

import com.esplibrary.client.ESPClientListener;
import com.esplibrary.client.ESPRequest;
import com.esplibrary.client.ResponseHandler;
import com.esplibrary.client.callbacks.NoDataListener;
import com.esplibrary.data.AlertBand;
import com.esplibrary.data.AlertDataFactory;
import com.esplibrary.packets.ESPPacket;
import com.esplibrary.packets.InfDisplayDataFactory;
import com.esplibrary.packets.PacketFactory;
import com.esplibrary.packets.PacketUtils;
import com.esplibrary.utilities.ESPLogger;
import com.esplibrary.client.callbacks.ESPRequestedDataListener;

import org.json.JSONObject;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class V1connectionTheiaWrapper extends V1connectionBaseWrapper implements GattCallback {

    /* callbacks for various functions */
    ESPRequestedDataListener<String> versionCallback;
    ESPClientListener mListener;

    public void setVersionCallback(ESPRequestedDataListener<String> c) {
        versionCallback = c;
    }


    public class TheiaGattCallback extends BluetoothGattCallback {
        private V1connectionTheiaWrapper w;

        TheiaGattCallback(V1connectionTheiaWrapper wr) {
            w = wr;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            w.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            w.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            w.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            w.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            w.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            w.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            w.onReadRemoteRssi(gatt, rssi, status);
        }
    }

    private final static String LOG_TAG = "TheiaV1cWrpr";

    private final TheiaGattCallback mGattCallback;
    protected BluetoothGatt mGatt;
    protected BluetoothGattCharacteristic mClientOut;
    private Handler mHandler;
    private boolean mNotifyOnDisconnection = false;
    private int mRSSI = -127;
    private RSSICallback mPendingRSSICB;

    protected final AtomicBoolean mCanWrite = new AtomicBoolean(false);

    /**
     * Constructs a Bluetooth Low-Energy {@link IV1connectionWrapper} instance.
     *
     * @param listener        The {@link ESPClientListener callback} that will be invoked when ESP data is
     *                        received.
     * @param factory         {@link PacketFactory} used to construct ESP packets.
     * @param timeoutInMillis Number of milliseconds before the
     *                        {@link NoDataListener#onNoDataDetected()} is invoked.
     */
    public V1connectionTheiaWrapper(@Nullable ESPClientListener listener, PacketFactory factory, long timeoutInMillis) {
        super(listener, factory, timeoutInMillis);
        // Create a V1GattCallback and pass a reference to our self so it pass along the GATT callbacks to us.
        // We do this because BluetoothGattCallback is an abstract class and we cannot have duel inheritance in java.
        mGattCallback = new TheiaGattCallback(this);
        mHandler = new Handler();
        mCanWrite.set(false);
        mListener = listener;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.Theia_BLE;
    }

    @Override
    public int getCachedRSSI() {
        synchronized (this) {
            return mRSSI;
        }
    }

    public BluetoothGatt getGatt() {
        return mGatt;
    }

    @Override
    public void connect(Context ctx, BluetoothDevice v1Device) {
        super.connect(ctx, v1Device);
        // Always reset the notify on disconnection
        mNotifyOnDisconnection = true;
        // Check to see if we are already connecting or connected and return.
        if (mState.get() == STATE_CONNECTING || mState.get() == STATE_CONNECTED) {
            return;
        }
        // Set the state to connecting.
        if (mState.compareAndSet(STATE_DISCONNECTED, STATE_CONNECTING)) {
            getHandler().obtainMessage(WHAT_CONNECTION_EVENT, ConnectionEvent.Connecting.ordinal(),
                    0).sendToTarget();
            // We need to keep a reference to mGatt right here so we can abort the connection
            // attempt if need be...
            setGATT(connectGatt(ctx, v1Device, mGattCallback));
            ESPLogger.d(LOG_TAG, "gatt connect called!");
        } else {
            // We were in the disconnected state while attempting to connect so force it to
            // DISCONNECTED and indicate the connection failed because we don't know why we weren't
            // in an expected state while connecting and the user should probably perform a
            // disconnect first.
            mState.set(STATE_DISCONNECTED);
            // We are in an unexpected state so indicate the connection failed.
            getHandler().obtainMessage(WHAT_CONNECTION_EVENT, ConnectionEvent.ConnectionFailed.ordinal(), 0).sendToTarget();
        }
    }

    /**
     * Connect to GATT Server hosted by this device. Caller acts as GATT client. The callback is used to deliver results to Caller,
     * such as connection status as well as any further GATT client operations. The method returns a BluetoothGatt instance.
     * You can use BluetoothGatt to conduct GATT client operations.
     * <p>
     * If the current device running API level is below 18 returns null.
     *
     * @param context
     * @param callback GATT callback respHandler that will receive asynchronous callbacks.
     * @return Returns a BluetoothGatt instance if an exception is not raised. Returns null if the current device's API level is below 18 (JELLY_BEAN_MR2).
     * @throws IllegalArgumentException if callback is null.
     */
    private static BluetoothGatt connectGatt(Context context, BluetoothDevice device, BluetoothGattCallback callback) {
        // ADD OREO SUPPORT IN SOON
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // TODO: 8/25/2017 ADD OREO SUPPORT INTO THE LIBRARY
            return device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M);
        }
        // Call the appropriate connGatt method based on the API level.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
        } else {
            return device.connectGatt(context, false, callback);
        }
    }

    @Override
    public void disconnect(boolean notifyDisconnect) {
        // If we are in the connecting state transition to the disconnected state and close the
        // BluetoothGatt object... we need to do this because calling disconnect before a connection
        // has been established doesn't guarantee onConnectionStateChange(BluetoothGatt, int int)
        // will be called thus allowing us to traditionally transition into the disconnected state.
        if (mState.get() == STATE_CONNECTING) {
            synchronized (this) {
                if (mGatt != null) {
                    mGatt.disconnect();
                    mGatt.close();
                }
                mGatt = null;
            }
            // Since the onConnectionStateChange won't be called, we must manually call
            // onDisconnected.
            onDisconnected(notifyDisconnect);
        } else {
            // The disconnect is asynchronous, so store the notify disconnect flag.
            mNotifyOnDisconnection = notifyDisconnect;
            // Call disconnect on the gatt object
            synchronized (this) {
                if (mGatt != null) {
                    mGatt.disconnect();
                }
            }
        }
        cancelServiceDiscoveryTimeout();
        super.disconnect(notifyDisconnect);
    }

    @Override
    protected void onDisconnected() {
        super.onDisconnected();
        synchronized (this) {
            mRSSI = -127;
            mPendingRSSICB = null;
        }
    }

    /**
     * Attempts to enable/disable notifications on both locally and on a remote device for the specified
     * characteristic.
     *
     * @param gatt    Bluetooth connection to a remote device.
     * @param charac  Characteristic on which to enable notifications
     * @param enabled True to enable notification
     * @return True if the enable/disable notifications was initiated.
     */
    protected boolean enableCharacteristicNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic charac, boolean enabled) {
        // If the Gatt object or the desired characteristic to be enabled/disabled, is null return false.
        if (gatt == null || charac == null) {
            return false;
        }
        gatt.setCharacteristicNotification(charac, enabled);
        BluetoothGattDescriptor descriptor = charac.getDescriptor(BTUtil.CLIENT_CHARACTERISTIC_CONFIG_CHARACTERISTIC_UUID);
        descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        return gatt.writeDescriptor(descriptor);
    }

    @Override
    public boolean canPerformBTWrite() {
        return mCanWrite.get();
    }

    @Override
    public void setCanPerformBTWrite(boolean canWrite) {
        mCanWrite.set(canWrite);
    }

    @Override
    protected boolean write(byte[] data) {
        BluetoothGatt gatt;
        BluetoothGattCharacteristic charac;
        synchronized (this) {
            gatt = mGatt;
            charac = mClientOut;
        }
        if (gatt == null || charac == null) {
            ESPLogger.d(LOG_TAG, String.format("write(byte[]) -> gatt == null : %b, charac == null : %b",
                    (gatt == null),
                    (charac == null)));
            return false;
        } else if (data == null) {
            ESPLogger.d(LOG_TAG, "write(byte[]) -> byte array == null");
            return false;
        }
        setCanPerformBTWrite(false);
        charac.setValue(data);
        return gatt.writeCharacteristic(charac);
    }

    //region GattCallback impl
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        // We wanna log the onConnectionStateChange call.
        ESPLogger.d(LOG_TAG, new StringBuilder("onConnectionState(")
                .append(BTUtil.gattOperationToString(status))
                .append(", ")
                .append(BTUtil.gattNewStateToString(newState))
                .append(")")
                .toString());

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (isConnecting()) {
                    ESPLogger.d(LOG_TAG, "Bluetooth Gatt connected");

                    BluetoothGattService leService = gatt.getService(BTUtil.THEIA_UUID);
                    if (leService == null) {
                        ESPLogger.d(LOG_TAG, String.format("V1connection LE Service is null after connecting to %s", BTUtil.getFriendlyName(gatt.getDevice())));
                    } else {
                        ESPLogger.d(LOG_TAG, String.format("V1connection LE Service none-null after connecting to %s", BTUtil.getFriendlyName(gatt.getDevice())));
                    }
                    // Discovery the available services.
                    gatt.discoverServices();
                    // We are in the correct state, we now wanna discover the devices services.
                    ESPLogger.d(LOG_TAG, "Discovering BluetoothGatt services...");
                    // We need to set a serv. discovery timeout because randomly the API will never invoke onServiceDiscovery(...) preventing the app to complete the BT connection
                    startServiceDiscoveryTimeout(gatt, 8000);
                    return;
                }
                ESPLogger.w(LOG_TAG, "Incorrect state: we weren't anticipating a connection, disconnecting.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Check to see if we were expecting the disconnecting state.
                if (mState.get() == STATE_DISCONNECTING) {
                    ESPLogger.d(LOG_TAG, "Successfully disconnected.");
                    gatt.close();
                    setGATT(null);
                    onDisconnected(mNotifyOnDisconnection);
                    return;
                }
                ESPLogger.w(LOG_TAG, "Incorrect state: we weren't anticipating a disconnection!");
            }
        }

        // Remember to call close(...) instead of disconnect() here. This is necessary because
        // calling  disconnect(...) will trigger a temporary disconnection but a few seconds later a
        // new connection is created. To prevent this we wanna close the BluetoothGatt object.
        setGATT(null);
        gatt.close();

        // If we made it to this point, and we're connecting, that means we've failed to establish
        // a successful connection.
        if (isConnecting()) {
            onConnectionFailed();
            return;
        } else if (isConnected()) {
            onConnectionLost();
            return;
        }
        // Whether failing to connect, or a connection loss transition into the disconnected state.
        onDisconnected(mNotifyOnDisconnection);
        mNotifyOnDisconnection = true;
    }

    protected void setGATT(BluetoothGatt gatt) {
        synchronized (this) {
            mGatt = gatt;
        }
    }

    /**
     * Starts a service discovery timeout using the specified timeout.
     *
     * @param gatt
     * @param timeout
     */
    protected void startServiceDiscoveryTimeout(BluetoothGatt gatt, long timeout) {
        mHandler.postDelayed(() -> {
            ESPLogger.d(LOG_TAG, "Failed to discover device services, disconnecting.");
            gatt.disconnect();
        }, timeout);
    }

    /**
     * Cancel the service discovery timeout
     */
    protected void cancelServiceDiscoveryTimeout() {
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        // Clear the service discovery timeout.
        cancelServiceDiscoveryTimeout();
        ESPLogger.d(LOG_TAG, new StringBuilder("onServicesDiscovered(")
                .append(BTUtil.gattOperationToString(status))
                .append(")")
                .toString());
        // If the service discovery was successful, enable notifications for the V1-out, client-in characteristic.
        if (status == BluetoothGatt.GATT_SUCCESS) {
            List<BluetoothGattService> l = gatt.getServices();


            // Make sure we were in the correct state when services were discovered.
            if (mState.get() == STATE_CONNECTING) {
                discoveryESPGATTCharacteristics(gatt);
                onConnected();
                getHandler().obtainMessage(WHAT_CONNECTION_EVENT, ConnectionEvent.Connected.ordinal(),
                        0).sendToTarget();
            }
        } else {
            // If we failed to discover services while in the connecting state, disconnect because we aren't able to find the V1connectionWrapper LE service.
            if (mState.get() == STATE_CONNECTING) {
                ESPLogger.d(LOG_TAG, "Failed to discover V1connection LE service.");
                gatt.disconnect();
            }
        }
    }

    protected void discoveryESPGATTCharacteristics(BluetoothGatt gatt) {
        //mClientOut = service.getCharacteristic(BTUtil.CLIENT_OUT_V1_IN_SHORT_CHARACTERISTIC_UUID);
        //BluetoothGattCharacteristic v1OutClientIn = service.getCharacteristic(BTUtil.V1_OUT_CLIENT_IN_SHORT_CHARACTERISTIC_UUID);
        ESPLogger.d(LOG_TAG, "Enabling notifications for V1-Out/Client-In short BluetoothGattCharacteristic...");
        //enableCharacteristicNotifications(gatt, v1OutClientIn, true);
        BluetoothGattService l = gatt.getService(UUID.fromString(TheiaUtil.UUID_STR_SERVICE));
        BluetoothGattCharacteristic c = l.getCharacteristic(UUID.fromString(TheiaUtil.UUID_STR_DISPLAY));
        //gatt.setCharacteristicNotification(c, true);
        enableCharacteristicNotifications(gatt, c, true);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        ESPLogger.d(LOG_TAG, new StringBuilder("onDescriptorWrite(")
                .append(BTUtil.gattOperationToString(status))
                .append(", Descriptor UUID:")
                .append(descriptor.getUuid().toString())
                .append(")")
                .toString());

        // As of right now, the only descriptor write the V1connectionLEWrapper performs is enabling
        // notifications for the V1-out, client-in characteristic, during the connection process,
        // so if the status is successful,
        if (descriptor.getCharacteristic().getUuid().equals(BTUtil.V1_OUT_CLIENT_IN_SHORT_CHARACTERISTIC_UUID)) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onConnected();
                return;
            }
            // If we reached this point, that means we failed to enable notifications for the V1-Out
            // Short characteristics and we should disconnect because data communications aren't
            // possible.
            ESPLogger.d(LOG_TAG, "Failed to enable notifications for the V1-out, client-in short characteristic.");
            // Fall-through to the code below, and disconnect.
            gatt.disconnect();
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        // Whenever, we receive this callback, we are able to to write again to the V1-in, client-out characteristic.
        setCanPerformBTWrite(true);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            byte[] value = characteristic.getValue();
            ESPLogger.e(LOG_TAG, String.format("%s failed to write: %s", characteristic.getUuid().toString(), BTUtil.toHexString(value)));

            final ResponseHandler respHndlr = getResponseProcessor().removeResponseHandlerForData(value);
            // Remove queued request that has the same response expector
            synchronized (mRequestQueue) {
                for (int i = mRequestQueue.size() - 1; i >= 0; i--) {
                    final ESPRequest espRequest = mRequestQueue.get(i);
                    if (espRequest.respHandler == respHndlr) {
                        mRequestQueue.remove(i);
                    }
                }
            }
            // Fail the response handler
            if (respHndlr != null) {
                if (respHndlr.failureCallback != null) {
                    respHndlr.failureCallback.onFailure("BTError: Failed to send ESPPacket");
                }
            }
        }
    }


    public void OnCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        // Only process Characteristics on the V1-out, client-in short BTGatt characteristic.
        if (characteristic.getUuid().equals(BTUtil.V1_OUT_CLIENT_IN_SHORT_CHARACTERISTIC_UUID)) {
            byte[] data = characteristic.getValue();
            mBuffer.addAll(data);
            ESPPacket packet = PacketUtils.makeFromBufferLE(mFactory, mBuffer, mLastV1Type);
            // If the packet is null perform send a malformed data msg.
            if (packet == null) {
                malformedData(data);
                return;
            }
            // Ignore echo packets.
            if (checkForEchos(packet)) {
                return;
            }
            // Perform ESP processing.
            processESPPacket(packet);
        } else if (characteristic.getUuid().equals(UUID.fromString(TheiaUtil.UUID_STR_DISPLAY)))
        {
            String display = characteristic.getStringValue(0);
            try {
                JSONObject jo = new JSONObject(display);
                int type = jo.getInt("alert_class");

                InfDisplayDataFactory f = new InfDisplayDataFactory();



                if (type == TheiaUtil.ALERT_CLASS_NONE) {
                    mListener.onDisplayDataReceived(f.getInfDisplayData());
                    return;
                }

                if (type == TheiaUtil.ALERT_CLASS_LASER)
                {
                    f.setLaser(true);
                    mListener.onDisplayDataReceived(f.getInfDisplayData());
                    return;
                }
                else if (type == TheiaUtil.ALERT_CLASS_RADAR) {

                    int dir = jo.getInt("dir");
                    double intensity = jo.getDouble("intensity");
                    int band = jo.getInt("band");
                    double freq = jo.getDouble("frequency");

                    AlertDataFactory alertFactory = new AlertDataFactory();
                    alertFactory.setFrequency((int)freq);

                    switch(dir)
                    {
                        case TheiaUtil.ALERT_DIR_FRONT:
                            f.setFront(true);
                            break;
                        case TheiaUtil.ALERT_DIR_SIDE:
                            f.setSide(true);
                            break;
                        case TheiaUtil.ALERT_DIR_REAR:
                            f.setRear(true);
                            break;
                    }

                    switch (band) {
                        case TheiaUtil.ALERT_BAND_X:
                            f.setX(true);
                            alertFactory.setBand(AlertBand.X);
                            break;
                        case TheiaUtil.ALERT_BAND_K:
                            f.setK(true);
                            alertFactory.setBand(AlertBand.K);
                            break;
                        case TheiaUtil.ALERT_BAND_KA:
                            f.setKa(true);
                            alertFactory.setBand(AlertBand.Ka);
                            break;
                        default:
                            break;

                    }

                    mListener.onDisplayDataReceived(f.getInfDisplayData());
                }

            }
            catch (Exception e)
            {

            }
        }
        else {
            ESPLogger.d(LOG_TAG, "Unsupported characteristic. UUID: " + characteristic.getUuid().toString());
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            final BluetoothDevice device = gatt.getDevice();
            final RSSICallback cb;
            synchronized (this) {
                mRSSI = rssi;
                cb = mPendingRSSICB;
                mPendingRSSICB = null;
            }
            if (cb != null) {
                mHandler.post(() -> cb.onRssiReceived(device, rssi));
            }
        } else {
            ESPLogger.e(LOG_TAG, "Unable to read the remote device's RSSI");
        }
    }

    @Override
    public boolean readRemoteRSSI(RSSICallback callback) {
        if (isConnected()) {
            synchronized (this) {
                mPendingRSSICB = callback;
            }
            return mGatt.readRemoteRssi();
        }
        ESPLogger.d(LOG_TAG, "Not connected - unable to read remote RSSI!");
        return false;
    }


    boolean t = false;
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        t = !t;
        if (status == 0) {
            if (0 == characteristic.getUuid().compareTo(UUID.fromString(TheiaUtil.UUID_STR_GPS))) {
                String res = characteristic.getStringValue(0);
                if (versionCallback != null)
                    versionCallback.onDataReceived(res, null);
                versionCallback = null;
                InfDisplayDataFactory f = new InfDisplayDataFactory();
                f.setFront(t);
                mListener.onDisplayDataReceived(f.getInfDisplayData());
                return;
            }

            String res = characteristic.getStringValue(0);
            ESPLogger.e("ONCharacteristic Read ", "onCharacteristicRead: " + res);
            return;
        } else {
            ESPLogger.e("ONCHARREAD", "Got status = " + String.valueOf(status));
        }
    }

    /* here we register all the callbacks from the client */

}
