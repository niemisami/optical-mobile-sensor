package com.niemisami.opticalmobilesensor.SignalProcessing;

import org.junit.Assert;
import org.testng.annotations.Test;

/**
 * Created by sakrnie on 5.7.2016.
 */
public class DSPTest2 {

    private int[] values = new int[]{1,1,1,1,2,2,2,3,3,5,0,0,1,0,4,4,3,7};
    private int[] instances = new int[] {1,2,3,5,0,4,7};
    private int[] weights = new int[]   {5,3,3,5,3,2,1};

    @Test
    public void testGetWeights() throws Exception {
        Assert.assertArrayEquals(weights, DSP.getWeights(instances,values));

    }

    @org.junit.Test
    public void testGetInstances() throws Exception {
        Assert.assertArrayEquals(instances, DSP.getInstances(values));

    }

}