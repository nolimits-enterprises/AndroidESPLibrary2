package com.esplibrary.packets;

import android.bluetooth.BluetoothGattCharacteristic;

import com.esplibrary.packets.InfDisplayData;

/*
      factory for creating an InfDisplayData
 */
public class InfDisplayDataFactory {

    InfDisplayData d;

    /*Constants for accessing the BOGEY COUNTER IMAGE 1 byte inside of the payload array.*/
    private static final int BOGEY_COUNTER_IMAGE_IDX = PacketUtils.PAYLOAD_START_IDX + 0;
    /*Constants for accessing the BOGEY COUNTER IMAGE 2 byte inside of the payload array.*/
    private static final int BOGEY_COUNTER_IMAGE2_IDX = PacketUtils.PAYLOAD_START_IDX + 1;
    /*Constants for accessing the BARGRAPH SIGNAL STRENGTH byte inside of the payload array.*/
    private static final int BAR_GRAPH_SIGNAL_STRENGTH_IMAGE_IDX = PacketUtils.PAYLOAD_START_IDX + 2;
    /*Constants for accessing the BAND ARROW IMAGE 1 byte inside of the payload array.*/
    private static final int BAND_ARROW_IND_IMAGE_IDX = PacketUtils.PAYLOAD_START_IDX + 3;
    /*Constants for accessing the BAND ARROW IMAGE 2 byte inside of the payload array.*/
    private static final int BAND_ARROW_IND_IMAGE2_IDX = PacketUtils.PAYLOAD_START_IDX + 4;
    /*Constants for accessing the AUX 0 byte inside of the payload array.*/
    private static final int AUX_0_IDX = PacketUtils.PAYLOAD_START_IDX + 5;
    /*Constants for accessing the AUX 1 byte inside of the payload array.*/
    private static final int AUX_1_IDX = PacketUtils.PAYLOAD_START_IDX + 6;
    /*Constants for accessing the AUX 2 byte inside of the payload array.*/
    private static final int AUX_2_IDX = PacketUtils.PAYLOAD_START_IDX + 7;

    private static final int CAPITAL_C = 0x39;
    private static final int CAPITAL_U = 0x3E;
    private static final int LOWER_CASE_U = 0x1C;
    private static final int LOWER_CASE_C = 0x58;
    private static final int CAPITAL_L = 0x38;
    private static final int LOWER_CASE_L = 0x18;
    private static final int CAPITAL_A = 0x77;

    /*Bit Mask for accessing the Mute Indicator in both Band and Arrow Image 1 & Image 2*/
    private static final int MUTE_INDICATOR_MASK = 0x10;
    /*Bit Mask for accessing the Bluetooth indicator image 1*/
    private static final int BLUETOOTH_INDICATOR_IMG_1_MASK = 0x40;
    /*Bit Mask for accessing the Bluetooth indicator image 2*/
    private static final int BLUETOOTH_INDICATOR_IMG_2_MASK = 0x80;


    public void setLaser(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x01;
        if (s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x01;
    }

    public void setKa(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x02;
        if (s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x02;
    }

    public void setX(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x08;
        if (s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x08;
    }

    public void setK(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x04;
        if (s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x04;
    }

    public void setFront(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x20;
        if(s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x20;
    }

    public void setSide(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x40;
        if(s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x40;
    }

    public void setRear(boolean s)
    {
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] &= ~0x80;
        if(s)
            d.packetData[BAND_ARROW_IND_IMAGE_IDX] |= 0x80;
    }

    void setSystemStatus(boolean s)
    {
        d.packetData[AUX_0_IDX] &= ~0x04;
        if (s)
            d.packetData[AUX_0_IDX] |= 0x04;
    }

    void setDisplay(boolean s)
    {
        d.packetData[AUX_0_IDX] &= ~0x08;
        if (s)
            d.packetData[AUX_0_IDX] |= 0X08;
    }

    void setBarGraph(int i)
    {
        if (i < 0)
            i = 0;

        if (i > 7)
            i = 7;

        d.packetData[BAR_GRAPH_SIGNAL_STRENGTH_IMAGE_IDX] = 0;
        for (int j = 0; j < i; j++)
        {
            d.packetData[BAR_GRAPH_SIGNAL_STRENGTH_IMAGE_IDX] |= 1 << j;

        }
    }

    public InfDisplayDataFactory() {
        d = new InfDisplayData(PacketUtils.PAYLOAD_START_IDX+8);
        d.packetData[BOGEY_COUNTER_IMAGE_IDX] = 0;
        d.packetData[BOGEY_COUNTER_IMAGE2_IDX] = 0;
        d.packetData[BAR_GRAPH_SIGNAL_STRENGTH_IMAGE_IDX] = 0;
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] = 0;
        d.packetData[BAND_ARROW_IND_IMAGE_IDX] = 0;
        d.packetData[BAND_ARROW_IND_IMAGE2_IDX] = 0;
        d.packetData[AUX_0_IDX] = 0;
        d.packetData[AUX_1_IDX] = 0;
        d.packetData[AUX_2_IDX] = 0;

        setSystemStatus(true);
    }

    public InfDisplayData getInfDisplayData() {

        return d;
    }
}
