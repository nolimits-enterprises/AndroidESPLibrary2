package com.esplibrary.data;

public class AlertDataFactory {
    protected static final int ALERT_INDEX_COUNT_IDX = 0;
    protected static final int FREQUENCY_MSB_IDX = 1;
    protected static final int FREQUENCY_LSB_IDX = 2;
    protected static final int FRONT_SIGNAL_STRENGTH_IDX = 3;
    protected static final int REAR_SIGNAL_STRENGTH_IDX = 4;
    protected static final int BAND_ARROW_DEF_IDX = 5;
    protected static final int AUX_BYTE_IDX = 6;

    byte[] d;

    public void setIndex(int i)
    {
        if (i < 0)
            i = 0;
        if (i > 0x0f)
            i = 0x0f;

        d[ALERT_INDEX_COUNT_IDX] &= 0x0f;
        d[ALERT_INDEX_COUNT_IDX] |= i << 4;
    }

    public void setCount(int i )
    {
        if (i < 0)
            i = 0;
        if (i > 0x0f)
            i = 0x0f;

        d[ALERT_INDEX_COUNT_IDX] &= 0xf0;
        d[ALERT_INDEX_COUNT_IDX] |= i;
    }

    public void setFrequency(int f)
    {
        d[FREQUENCY_MSB_IDX] = (byte)(((f & 0xff00) >> 8) & 0xff);
        d[FREQUENCY_LSB_IDX] = (byte)(f & 0x00ff);
    }

    public void setFrontStrength(int f)
    {
        d[FRONT_SIGNAL_STRENGTH_IDX] = (byte)(f & 0xff);
    }

    public void setRearStrength(int f)
    {
        d[REAR_SIGNAL_STRENGTH_IDX] = (byte)(f & 0xff);
    }

    public void setBand(AlertBand b)
    {
        d[BAND_ARROW_DEF_IDX] &= ~0x1f;
        d[BAND_ARROW_DEF_IDX] |= 0x1f &  b.toByte();
    }

    public void setDirection(Direction dir)
    {
        d[BAND_ARROW_DEF_IDX] &= ~0xe0;
        d[BAND_ARROW_DEF_IDX] |= dir.toByte() & 0xe0;
    }

    public AlertDataFactory()
    {
        d = new byte[7];
        for (int i = 0; i < 7; i++)
            d[i] = 0;
    }

    public AlertData getAlertData()
    {
        return new AlertData(d, 0, 7);
    }

}
