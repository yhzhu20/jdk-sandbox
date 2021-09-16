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
import java.net.UnknownHostException;

import static insp.ThrowingLookupsProviderImpl.THROW_RUNTIME_EXCEPTION;
import static insp.ThrowingLookupsProviderImpl.RUNTIME_EXCEPTION_MESSAGE;

import org.testng.Assert;
import org.testng.annotations.Test;


/*
 * @test
 * @summary Test that only UnknowHostException is thrown if name service implementation
 *  throws runtime exception during forward or reverse lookup.
 * @library providers/throwing
 * @build throwing.lookups.provider/insp.ThrowingLookupsProviderImpl
 * @run testng/othervm ResolutionWithExceptionTest
 * @run testng/othervm -Dprovider.throws.runtime.exception=true ResolutionWithExceptionTest
 */

public class ResolutionWithExceptionTest {

    @Test
    public void getByNameTest() {
        // InetAddress.getByName() is expected to throw UnknownHostException in all cases
        UnknownHostException uhe = Assert.expectThrows(UnknownHostException.class,
                () -> InetAddress.getByName("doesnt.matter.com"));
        // If provider is expected to throw RuntimeException - check that UnknownHostException
        // has it set as cause
        if (THROW_RUNTIME_EXCEPTION) {
            Throwable cause = uhe.getCause();
            if (cause instanceof RuntimeException re) {
                // Check RuntimeException message
                Assert.assertEquals(re.getMessage(), RUNTIME_EXCEPTION_MESSAGE,
                        "incorrect exception message");
            } else {
                Assert.fail("UnknownHostException cause is not RuntimeException");
            }
        }
    }

    @Test
    public void getByAddressTest() throws UnknownHostException {
        // getCanonicalHostName is not expected to throw an exception:
        // if there is an error during reverse lookup operation the literal IP
        // address String will be returned.
        String literalIP = InetAddress.getByAddress(new byte[]{1, 2, 3, 4}).getCanonicalHostName();
        Assert.assertEquals(literalIP, "1.2.3.4");
    }
}
