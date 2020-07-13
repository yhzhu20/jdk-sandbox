/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Test for Runtime.fieldSizeOf
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m -Xint                   FieldOffsetOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=1 FieldOffsetOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=2 FieldOffsetOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=3 FieldOffsetOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=4 FieldOffsetOf
 * @run main/othervm -Xmx128m -XX:-TieredCompilation  FieldOffsetOf
 */

import java.lang.reflect.Field;

public class FieldSizeOf {

    public static void main(String ... args) throws Exception {
        testOffsets();
        testNulls();
    }

    private static testOffsets() throws Exception {
        Field fBoolean = Target.class.getDeclaredField("f_boolean");
        Field fByte    = Target.class.getDeclaredField("f_byte");
        Field fChar    = Target.class.getDeclaredField("f_char");
        Field fShort   = Target.class.getDeclaredField("f_short");
        Field fInt     = Target.class.getDeclaredField("f_int");
        Field fFloat   = Target.class.getDeclaredField("f_float");
        Field fLong    = Target.class.getDeclaredField("f_long");
        Field fDouble  = Target.class.getDeclaredField("f_double");
        Field fObject  = Target.class.getDeclaredField("f_object");

        for (int c = 0; c < 100000; c++) {
           RuntimeOfUtil.assertEquals(1, Runtime.fieldSizeOf(fBoolean));
           RuntimeOfUtil.assertEquals(1, Runtime.fieldSizeOf(fByte));
           RuntimeOfUtil.assertEquals(2, Runtime.fieldSizeOf(fChar));
           RuntimeOfUtil.assertEquals(2, Runtime.fieldSizeOf(fShort));
           RuntimeOfUtil.assertEquals(4, Runtime.fieldSizeOf(fInt));
           RuntimeOfUtil.assertEquals(4, Runtime.fieldSizeOf(fFloat));
           RuntimeOfUtil.assertEquals(8, Runtime.fieldSizeOf(fLong));
           RuntimeOfUtil.assertEquals(8, Runtime.fieldSizeOf(fDouble));
           RuntimeOfUtil.assertEquals(4, Runtime.fieldSizeOf(fObject)); // TODO: Assumes compressed oops
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.fieldSizeOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

    public static Target {
        boolean f_boolean;
        byte    f_byte;
        char    f_char;
        short   f_short;
        int     f_int;
        float   f_float;
        double  f_double;
        long    f_long;
        Object  f_object;
    }

}
