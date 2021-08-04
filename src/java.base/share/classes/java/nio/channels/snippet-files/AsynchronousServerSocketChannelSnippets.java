/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import static jdk.internal.misc.Signal.handle;

/**
 * Snippets used in AsynchronousServerSocketChannelSnippets.
 */ 

final class AsynchronousServerSocketChannelSnippets {
    CompletionHandler cher;

    // @start region=snippet1 :
  final AsynchronousServerSocketChannel listener =
      AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(5000));
    private void snippet1() { //@replace replacement=""
        listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

                    public void completed(AsynchronousSocketChannel ch, Void att) {
                        // accept the next connection
                        listener.accept(null, cher); //@replace regex="cher" replacement="this"

                        // handle this connection
                        handle(ch);
                    }

                    public void failed(Throwable exc, Void att) {
                        //... //@replace regex="//" replacement=""
                    }
                }
        );
// @end snippet1
    }
    private void handle(AsynchronousSocketChannel ch) {
    }

    AsynchronousServerSocketChannelSnippets() throws IOException {
    }

}
