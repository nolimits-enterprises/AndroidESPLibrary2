package com.esplibrary.client;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.esplibrary.bluetooth.BTUtil;
import com.esplibrary.bluetooth.ConnectionListener;
import com.esplibrary.bluetooth.ConnectionType;
import com.esplibrary.bluetooth.IV1connectionWrapper;
import com.esplibrary.bluetooth.RSSICallback;
import com.esplibrary.client.callbacks.ESPRequestListener;
import com.esplibrary.client.callbacks.ESPRequestedDataListener;
import com.esplibrary.client.callbacks.MalformedDataListener;
import com.esplibrary.client.callbacks.NoDataListener;
import com.esplibrary.client.callbacks.NotificationListener;
import com.esplibrary.constants.DeviceId;
import com.esplibrary.constants.PacketId;
import com.esplibrary.constants.V1Mode;
import com.esplibrary.data.SAVVYStatus;
import com.esplibrary.data.SweepData;
import com.esplibrary.data.SweepDefinition;
import com.esplibrary.data.SweepSection;
import com.esplibrary.data.UserSettings;
import com.esplibrary.packets.ESPPacket;
import com.esplibrary.packets.InfDisplayData;
import com.esplibrary.packets.request.RequestAllSweepDefinitions;
import com.esplibrary.packets.request.RequestBatteryVoltage;
import com.esplibrary.packets.request.RequestChangeMode;
import com.esplibrary.packets.request.RequestDefaultSweepDefinitions;
import com.esplibrary.packets.request.RequestDefaultSweeps;
import com.esplibrary.packets.request.RequestFactoryDefault;
import com.esplibrary.packets.request.RequestMaxSweepIndex;
import com.esplibrary.packets.request.RequestMuteOff;
import com.esplibrary.packets.request.RequestMuteOn;
import com.esplibrary.packets.request.RequestOverrideThumbwheel;
import com.esplibrary.packets.request.RequestSAVVYStatus;
import com.esplibrary.packets.request.RequestSavvyUnmuteEnable;
import com.esplibrary.packets.request.RequestSerialNumber;
import com.esplibrary.packets.request.RequestStartAlertData;
import com.esplibrary.packets.request.RequestStopAlertData;
import com.esplibrary.packets.request.RequestSweepSections;
import com.esplibrary.packets.request.RequestTurnOffMainDisplay;
import com.esplibrary.packets.request.RequestTurnOnMainDisplay;
import com.esplibrary.packets.request.RequestUserBytes;
import com.esplibrary.packets.request.RequestVehicleSpeed;
import com.esplibrary.packets.request.RequestVersion;
import com.esplibrary.packets.request.RequestWriteSweepDefinition;
import com.esplibrary.packets.request.RequestWriteUserBytes;
import com.esplibrary.packets.response.ResponseAlertData;
import com.esplibrary.packets.response.ResponseBatteryVoltage;
import com.esplibrary.packets.response.ResponseMaxSweepIndex;
import com.esplibrary.packets.response.ResponseSAVVYStatus;
import com.esplibrary.packets.response.ResponseSerialNumber;
import com.esplibrary.packets.response.ResponseSweepSections;
import com.esplibrary.packets.response.ResponseSweepWriteResult;
import com.esplibrary.packets.response.ResponseUserBytes;
import com.esplibrary.packets.response.ResponseVehicleSpeed;
import com.esplibrary.packets.response.ResponseVersion;
import com.esplibrary.utilities.ESPLogger;
import com.esplibrary.utilities.V1VersionInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concrete implementation of the ESP Client interface.
 */
public class ESPTheiaClient implements IESPClient {

    private final static String LOG_TAG = "ESPTheiaClient";

    /**
     * Underlying bluetooth connection.
     */
    private final IV1connectionWrapper mConnection;
    /**
     * Application context used for performing various actions in the library, such as connecting and scanning.
     */
    private final Context mAppCtx;

    public ESPTheiaClient(Context appContext, IV1connectionWrapper connection) {
        mConnection = connection;
        mAppCtx = appContext.getApplicationContext();
    }

    /**
     * Returns the underlying V1connection wrapper impl.
     *
     * @return Underlying V1connection wrapper
     */
    protected IV1connectionWrapper getConnectionWrapper() {
        return mConnection;
    }

    @Override
    public boolean isLegacy() {
        return false;
    }

    //region State methods
    @Override
    public int getDataTimeout() {
        return (int) (mConnection.getDataTimeout() / 1000);
    }

    @Override
    public void setDataTimeout(int timeoutInSeconds) {
        // We need to convert them timeout ins seconds to milliseconds
        mConnection.setDataTimeout(timeoutInSeconds * 1000);
    }

    @Override
    public long getDataTimeoutMillis() {
        return mConnection.getDataTimeout();
    }

    @Override
    public void setDataTimeout(long timeoutInSeconds) {
        mConnection.setDataTimeout(timeoutInSeconds);
    }

    @Override
    public void setDemoData(String demoData) {
        mConnection.setDemoData(demoData);
    }

    @Override
    public void repeatDemoData(boolean repeat) {
        mConnection.repeatDemoMode(repeat);
    }

    @Override
    public void protectLegacyMode(boolean protect) {
        mConnection.protectLegacyMode(protect);
    }
    //endregion

    //region V1 info methods
    @Override
    public DeviceId getValentineType() {
        return DeviceId.THEIA_DEVICE;
    }

    @Override
    public boolean areDefaultSweepDefinitionsAvailableForV1Version(double v1Version) {
        return true;
    }
    //endregion

    //region Callback Registration methods
    @Override
    public void setESPClientListener(ESPClientListener listener) {
        mConnection.setESPClientListener(listener);
    }

    @Override
    public void clearESPClientListener() {
        mConnection.clearESPClientListener();
    }

    @Override
    public void addConnectionListener(ConnectionListener listener) {
        mConnection.addConnectionListener(listener);
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        mConnection.removeConnectionListener(listener);
    }

    @Override
    public void setMalformedListener(MalformedDataListener listener) {
        mConnection.setMalformedListener(listener);
    }

    @Override
    public void clearMalformedListener() {
        mConnection.clearMalformedListener();
    }

    @Override
    public void setNoDataListener(NoDataListener listener) {
        mConnection.setNoDataListener(listener);
    }

    @Override
    public void clearNoDataListener() {
        mConnection.clearNoDataListener();
    }

    @Override
    public void setNotificationListener(NotificationListener listener) {
        mConnection.setNotificationListener(listener);
    }

    @Override
    public void clearNotificationListener() {
        mConnection.clearNotificationListener();
    }
    //endregion

    //region Connection methods
    @Override
    public boolean isConnected() {
        return mConnection.isConnected();
    }

    @Override
    public boolean isConnecting() {
        return mConnection.isConnecting();
    }

    @Override
    public boolean isDemoMode() {
        return mConnection.getConnectionType() == ConnectionType.Demo && mConnection.isConnected();
    }

    @Override
    public void disconnect() {
        mConnection.disconnect(true);
    }

    @Override
    public void disconnect(boolean notifyOnDisconnect) {
        mConnection.disconnect(notifyOnDisconnect);
    }

    @Override
    public boolean connect(BluetoothDevice v1Device, ConnectionType connType, @Nullable ConnectionListener listener) {
        // Return false if the device doesn't support BT.
        if(!BTUtil.isBluetoothSupported(mAppCtx)) {
            ESPLogger.e(LOG_TAG, "Bluetooth isn't supported by this device");
            return false;
        }
        // Return false if an invalid connection type is provided
        else if(connType == ConnectionType.Invalid) {
            ESPLogger.e(LOG_TAG, "Invalid connection type!");
            return false;
        }
        // Return false if the connection type of the BT device provided isn't supported
        else if (connType == ConnectionType.LE && !BTUtil.isLESupported(mAppCtx)) {
            ESPLogger.e(LOG_TAG, "Bluetooth LE is not supported!");
            return false;
        }
        // Return false if the provided connection type doesn't match the bluetooth wrapper
        else if(connType != mConnection.getConnectionType()) {
            ESPLogger.e(LOG_TAG, "Invalid connection type; Expected connection type = " + mConnection.getConnectionType());
            return false;
        }
        // Register the conn. event listener
        mConnection.addConnectionListener(listener);
        mConnection.connect(mAppCtx, v1Device);
        return true;
    }

    @Override
    public boolean connectDemo(String demoESPData, @Nullable ConnectionListener listener) {
        if(ConnectionType.Demo != mConnection.getConnectionType()) {
            ESPLogger.e(LOG_TAG, "ESPClient's connection type isn't suitable for demo mode");
            return false;
        }
        // Register the conn. event listener
        mConnection.addConnectionListener(listener);
        mConnection.setDemoData(demoESPData);
        mConnection.connect(mAppCtx, null);
        return true;
    }

    @Override
    public ConnectionType getConnectionType() {
        return mConnection.getConnectionType();
    }

    @Override
    public BluetoothDevice getConnectedDevice() {
        return mConnection.getDevice();
    }

    @Override
    public int getConnectedDeviceRSSI() {
        return mConnection.getCachedRSSI();
    }

    @Override
    public boolean readConnectedDeviceRSSI(@NonNull RSSICallback callback) {
        return mConnection.readRemoteRSSI(callback);
    }

    //endregion

    //region ESP Data Request methods
    @Override
    public void requestVersion(DeviceId deviceID, ESPRequestedDataListener<String> callback) {
    }

    @Override
    public void requestVersionAsDouble(DeviceId device, ESPRequestedDataListener<Double> callback) {
    }

    @Override
    public void requestSerialNumber(DeviceId deviceID, ESPRequestedDataListener<String> callback) {
    }

    @Override
    public void requestUserSettings(double v1Version, ESPRequestedDataListener<UserSettings> callback) {
    }

    @Override
    public void requestUserBytes(ESPRequestedDataListener<byte[]> callback) {
    }

    @Override
    public void requestWriteUserBytes(byte[] userBytes, ESPRequestListener callback) {
    }

    @Override
    public void requestMaxSweepIndex(ESPRequestedDataListener<Integer> callback) {
    }

    @Override
    public void requestSweepSections(ESPRequestedDataListener<List<SweepSection>> callback) {
    }

    @Override
    public void requestAllSweepDefinitions(ESPRequestedDataListener<List<SweepDefinition>> callback) {
    }

    @Override
    public void requestDefaultSweeps(ESPRequestListener callback) {
    }

    @Override
    public void requestDefaultSweepDefinitions(ESPRequestedDataListener<List<SweepDefinition>> callback) {
    }

    /**
     * Helper method that can packet either the default {@link SweepDefinition sweeps} or normal {@link SweepDefinition sweeps}.
     *
     * @param responseID    Packet Id of the ESP response
     * @param maxSweepIndex maximum number of sweeps
     * @param callback  Callback that will be invoked once the sweep definitions are received
     */
    private void requestSweepDefinitions(boolean defaultSweeps, int responseID, int maxSweepIndex, ESPRequestedDataListener<List<SweepDefinition>> callback) {

    }

    @Override
    public void requestSweepData(double v1Version, ESPRequestedDataListener<SweepData> callback) {
    }

    @Override
    public void requestSAVVYStatus(ESPRequestedDataListener<SAVVYStatus> callback) {
        requestSAVVYStatus(callback, -1);
    }

    @Override
    public void requestSAVVYStatus(ESPRequestedDataListener<SAVVYStatus> callback, long requestTimeout) {
    }

    @Override
    public void requestOverrideThumbwheelToNone(ESPRequestListener callback) {
        requestOverrideThumbwheel(RequestOverrideThumbwheel.NONE, callback);
    }

    @Override
    public void requestOverrideThumbwheelToAuto(ESPRequestListener callback) {
        requestOverrideThumbwheel(RequestOverrideThumbwheel.AUTO, callback);
    }

    @Override
    public void requestOverrideThumbwheel(byte speed, ESPRequestListener callback) {
    }

    @Override
    public void requestSAVVYUnmute(boolean muteEnabled, ESPRequestListener callback) {
    }

    @Override
    public void requestVehicleSpeed(ESPRequestedDataListener<Integer> callback) {
    }

    @Override
    public void requestBatteryVoltage(ESPRequestedDataListener<String> callback) {
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void requestWriteSweepDefinitions(List<SweepDefinition> sweeps, ESPRequestedDataListener<Integer> callback) {
    }

    @Override
    public void requestMute(final boolean mute, ESPRequestListener callback) {
    }

    @Override
    public void requestChangeMode(V1Mode mode, ESPRequestListener callback) {
    }

    @Override
    public void requestDisplayOn(final boolean on, ESPRequestListener callback) {
    }

    @Override
    public void requestStartAlertData(ESPRequestListener callback) {
    }

    @Override
    public void requestStopAlertData(ESPRequestListener callback) {
        stopAlertData(callback, false);
    }

    @Override
    public void requestStopAlertDataImmediately(ESPRequestListener callback) {
        stopAlertData(callback, true);
    }

    /**
     *
     * @param callback Callback that will get fired once we've reasonably determined alert data has
     *                 stopped being received
     * @param sendNext Indicates if the stop at data request should be send next
     */
    private void stopAlertData(ESPRequestListener callback, boolean sendNext) {
    }

    @Override
    public void requestFactoryDefault(DeviceId device, ESPRequestListener callback) {
    }

    /**
     * Add an {@link ESPRequest} that will be sent out on the ESP bus. If not connected, this
     * request will automatically be failed.
     * @param request The {@link ESPRequest request} to be sent.
     *
     * @see #isConnected()
     */
    protected void addRequest(ESPRequest request) {
        if (request != null) {
            mConnection.addRequest(request);
        }
    }
    //endregion

    @Override
    public void destroy() {
        disconnect();
        // Unregister all listeners.
        mConnection.clearConnectionListeners();
        clearESPClientListener();
        clearNoDataListener();
        clearNotificationListener();
    }
}

