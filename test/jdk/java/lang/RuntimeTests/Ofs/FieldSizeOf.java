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
 *
 * @run main/othervm -XX:+CheckIntrinsics    FieldOffsetOf
 * @run main/othervm -Xint                   FieldOffsetOf
 * @run main/othervm -XX:TieredStopAtLevel=1 FieldOffsetOf
 * @run main/othervm -XX:TieredStopAtLevel=2 FieldOffsetOf
 * @run main/othervm -XX:TieredStopAtLevel=3 FieldOffsetOf
 * @run main/othervm -XX:TieredStopAtLevel=4 FieldOffsetOf
 * @run main/othervm -XX:-TieredCompilation  FieldOffsetOf
 */

import java.lang.reflect.Field;

public class FieldSizeOf {

    public static void main(String ... args) throws Exception {
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
           assertEquals(Runtime.fieldSizeOf(fBoolean), 1);
           assertEquals(Runtime.fieldSizeOf(fByte),    1);
           assertEquals(Runtime.fieldSizeOf(fChar),    2);
           assertEquals(Runtime.fieldSizeOf(fShort),   2);
           assertEquals(Runtime.fieldSizeOf(fInt),     4);
           assertEquals(Runtime.fieldSizeOf(fFloat),   4);
           assertEquals(Runtime.fieldSizeOf(fLong),    8);
           assertEquals(Runtime.fieldSizeOf(fDouble),  8);
           assertEquals(Runtime.fieldSizeOf(fObject),  4); // TODO: Assumes compressed oops
        }
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException("Error: expected: " + expected + ", actual: " + actual);
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
