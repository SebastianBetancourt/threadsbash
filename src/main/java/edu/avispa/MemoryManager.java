package edu.avispa;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

public class MemoryManager {
    
    protected final long totalMemory;
    private final int lowerBound;
    private final int upperBound;
    private final GlobalMemory memory;
    public final long modulationPeriod;

    MemoryManager(int lowerBound, int upperBound, long modulationPeriod){
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.memory = new SystemInfo().getHardware().getMemory();
        this.totalMemory = memory.getTotal();
        this.modulationPeriod = modulationPeriod;
    }

    protected double getUsedMemory(){
        return 100 - (100 * memory.getAvailable() / totalMemory);
    }

    protected boolean modulatingDown(){
        return getUsedMemory() > upperBound;
    }

    protected boolean modulatingUp(){
        return getUsedMemory() < lowerBound;
    }
}
