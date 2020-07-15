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
 * @summary Test for Runtime.deepSizeOf
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m -Xint                   DeepSizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=1 DeepSizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=2 DeepSizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=3 DeepSizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=4 DeepSizeOf
 * @run main/othervm -Xmx128m -XX:-TieredCompilation  DeepSizeOf
 */

import jdk.test.lib.Platform;

public class DeepSizeOf {

    public static void main(String ... args) {
        testSame_newObject();

        testNodeChain(0);
        testNodeChain(1);
        testNodeChain(10);
        testNodeChain(100);

        testObjArray(0);
        testObjArray(1);
        testObjArray(10);
        testObjArray(100);

        testNulls();
    }

    private static void testSame_newObject() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            Object o = new Object();
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(o), Runtime.deepSizeOf(o));
        }
    }

    private static void testNodeChain(int depth) {
        Node n = new Node(null);
        for (int d = 0; d < depth; d++) {
            n = new Node(n);
        }

        for (int c = 0; c < RuntimeOfUtil.SHORT_ITERS; c++) {
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(n)*(depth + 1), Runtime.deepSizeOf(n));
        }
    }

    private static class Node {
       Node next;
       public Node(Node n) { next = n; }
    }

    private static void testObjArray(int size) {
        Object o = new Object();
        Object[] arr = new Object[size];
        for (int d = 0; d < size; d++) {
            arr[d] = new Object();
        }

        for (int c = 0; c < RuntimeOfUtil.SHORT_ITERS; c++) {
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(arr) + Runtime.sizeOf(o)*size, Runtime.deepSizeOf(arr));
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.deepSizeOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

}
