package com.niemisami.opticalmobilesensor.SignalProcessing;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sakrnie on 5.7.2016.
 */
public class DSP {


    public static double getWeightedAverage(int[] array) {
        int[] instances = getInstances(array);
        int[] weights = getWeights(instances, array);

        if (instances.length != weights.length) {
            return 0.0;
        }

        double sumWeightedInstances = 0.0;
        double sumInstances = 0.0;
        for (int i = 0; i < instances.length; i++) {
            sumInstances += weights[i];
            sumWeightedInstances += instances[i] * weights[i];
        }

        return sumWeightedInstances / sumInstances;
    }

    public static double getAverage(int[] array) {
        double sum = 0.0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum / array.length;
    }

    public static int[] getWeights(int[] instances, int[] values) {
        int[] weights = new int[instances.length];
        for (int i = 0; i < instances.length; i++) {
            int weight = 0;
            for (int j = 0; j < values.length; j++) {
                if (values[j] == instances[i]) {
                    weight++;
                }
            }
            weights[i] = weight;
        }
        return weights;
    }

    public static int[] getInstances(int[] values) {
//        int[] instances = new int[values.length];
        List<Integer> instances = new ArrayList<>();
        instances.add(values[0]);
        boolean isInstance;
        for (int i : values) {
            isInstance = false;
            for (int j = 0; j < instances.size(); j++) {
                if (i == instances.get(j)) {
                    isInstance = true;
                    break;
                }
            }
            if (!isInstance) {
                instances.add(i);
            }
        }
        return listToArray(instances);
    }

    public static int[] listToArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
