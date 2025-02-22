package com.activeviam.test;

import static java.lang.foreign.ValueLayout.*;
import java.lang.foreign.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Benchmark the element wise aggregation of an array
 * of doubles into another array of doubles, using
 * combinations of  java arrays, byte buffers, standard java code
 * and the new Vector API.
 */
public class SumBenchmark {

    static final Unsafe U = getUnsafe();
    static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Manually launch JMH */
    public static void main(String[] params) throws Exception {
        Options opt = new OptionsBuilder()
            .include(SumBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    final static int SIZE = 1024;

    @State(Scope.Benchmark)
    public static class Data {

        final double[] inputArray;
        final ByteBuffer inputBuffer;
        final long inputAddress;
        final MemorySegment inputSegment;


        public Data() {
            this.inputArray = new double[SIZE];
            this.inputBuffer = ByteBuffer.allocateDirect(8 * SIZE);
            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.inputSegment = MemorySegment.allocateNative(8*SIZE, MemorySession.global());
        }
    }

    @Benchmark
    public double scalarArray(Data state) {
        final double[] input = state.inputArray;
        double sum = 0;
        for(int i = 0; i < SIZE; i++) {
            sum += input[i];
        }
        return sum;
    }

    @Benchmark
    public double unrolledArray(Data state) {
        final double[] input = state.inputArray;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < SIZE; i+=4) {
            sum1 += input[i];
            sum2 += input[i+1];
            sum3 += input[i+2];
            sum4 += input[i+3];
        }
        return sum1+sum2+sum3+sum4;
    }

    @Benchmark
    public double scalarArrayLongStride(Data state) {
        final double[] input = state.inputArray;
        double sum = 0;

        // Using a long counter defeats loop unrolling and then vectorization
        for(long i = 0; i < SIZE; i++) {
            sum += input[(int)i];
        }
        return sum;
    }

    static final VarHandle AH = MethodHandles.arrayElementVarHandle(double[].class);

    @Benchmark
    public double scalarArrayHandle(Data state) {
        final double[] input = state.inputArray;
        double sum = 0;
        for(int i = 0; i < input.length; i++) {
            sum += (double) AH.get(input, i);
        }
        return sum;
    }

    @Benchmark
    public double unrolledArrayHandle(Data state) {
        final double[] input = state.inputArray;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < input.length; i+=4) {
            sum1 += (double) AH.get(input, i);
            sum2 += (double) AH.get(input, i+1);
            sum3 += (double) AH.get(input, i+2);
            sum4 += (double) AH.get(input, i+3);
        }
        return sum1+sum2+sum3+sum4;
    }

    @Benchmark
    public double scalarUnsafe(Data state) {
        final long inputAddress = state.inputAddress;
        double sum = 0;
        for(int i = 0; i < SIZE; i++) {
            sum += U.getDouble(inputAddress + 8*i);
        }
        return sum;
    }

    @Benchmark
    public double unrolledUnsafe(Data state) {
        final long inputAddress = state.inputAddress;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < SIZE; i+=4) {
            sum1 += U.getDouble(inputAddress + 8*i);
            sum2 += U.getDouble(inputAddress + 8*(i+1));
            sum3 += U.getDouble(inputAddress + 8*(i+2));
            sum4 += U.getDouble(inputAddress + 8*(i+3));
        }
        return sum1+sum2+sum3+sum4;
    }


    static final VarHandle MHI = MemoryLayout.sequenceLayout(-1, JAVA_DOUBLE)
            .varHandle(MemoryLayout.PathElement.sequenceElement());

    @Benchmark
    public double scalarSegmentHandle(Data state) {
        final MemorySegment is = state.inputSegment;
        double sum = 0;
        for(int i = 0; i < SIZE; i++) {
            sum += (double) MHI.get(is, (long) i);
        }
        return sum;
    }

    @Benchmark
    public double unrolledSegment(Data state) {
        final MemorySegment is = state.inputSegment;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < SIZE; i+=4) {
            sum1 += (double) is.getAtIndex(JAVA_DOUBLE, (long)(i));
            sum2 += (double) is.getAtIndex(JAVA_DOUBLE, (long)(i+1));
            sum3 += (double) is.getAtIndex(JAVA_DOUBLE, (long)(i+2));
            sum4 += (double) is.getAtIndex(JAVA_DOUBLE, (long)(i+3));
        }
        return sum1+sum2+sum3+sum4;
    }

    @Benchmark
    public double unrolledSegmentHandle(Data state) {
        final MemorySegment is = state.inputSegment;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < SIZE; i+=4) {
            sum1 += (double) MHI.get(is, (long)(i));
            sum2 += (double) MHI.get(is, (long)(i+1));
            sum3 += (double) MHI.get(is, (long)(i+2));
            sum4 += (double) MHI.get(is, (long)(i+3));
        }
        return sum1+sum2+sum3+sum4;
    }
}
