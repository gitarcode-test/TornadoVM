package uk.ac.manchester.tornado.examples.spirv;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;

/**
 * Test used for generating OpenCL kernel and test the pre-compiled API with
 * SPIRV.
 * 
 * How to run?
 * 
 * <p>
 * tornado --igv --debug uk.ac.manchester.tornado.examples.spirv.TestSPIRV
 * </p>
 */
public class TestSPIRV {

    public static void copyTest(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void copyTestZero(int[] a) {
        a[0] = 50;
    }

    public static void sum(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 150);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::copyTest, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));
        if (a[0] == 50) {
            System.out.println("Result is CORRECT");
        }

    }
}
