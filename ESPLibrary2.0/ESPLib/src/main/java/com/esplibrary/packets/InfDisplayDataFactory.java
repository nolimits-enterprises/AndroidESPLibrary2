package com.esplibrary.packets;

import com.esplibrary.packets.InfDisplayData;

/*
      factory for creating an InfDisplayData 
 */
public class InfDisplayDataFactory {

    public InfDisplayDataFactory() {}

    public InfDisplayData getInfDisplayData() { return new InfDisplayData(1);}
}
