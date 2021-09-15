/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @test
 * @summary Test that InetAddress fast fails if custom provider fails to
 *    instantiate a name service.
 * @library providers/faulty
 * @build faulty.provider/insp.FaultyNameServiceProviderGetImpl
 * @run testng/othervm ProviderGetExceptionTest
 */

public class ProviderGetExceptionTest {

    @Test
    public void getByNameExceptionTest() throws Exception {
        String hostName = "test.host";
        System.out.println("Looking up address for the following host name:" + hostName);
        IllegalArgumentException iae = Assert.expectThrows(IllegalArgumentException.class,
                () -> InetAddress.getByName(hostName));
        System.out.println("Got exception of expected type:" + iae);
        assert iae.getCause() == null;
        Assert.assertEquals(iae.getMessage(), FAULT_MESSAGE);
    }

    @Test
    public void getByAddressExceptionTest() throws Exception {
        byte [] address = new byte[]{1, 2, 3, 4};
        System.out.println("Looking up host name for the following address:" + Arrays.toString(address));
        IllegalArgumentException iae = Assert.expectThrows(IllegalArgumentException.class,
                () -> InetAddress.getByAddress(address).getHostName());
        System.out.println("Got exception of expected type:" + iae);
        assert iae.getCause() == null;
        Assert.assertEquals(iae.getMessage(), FAULT_MESSAGE);
    }

    private static final String FAULT_MESSAGE = "This provider provides nothing";
}
