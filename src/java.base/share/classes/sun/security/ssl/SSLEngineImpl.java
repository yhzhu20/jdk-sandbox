/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSession;

/**
 * Implementation of an non-blocking SSLEngine.
 *
 * @author Brad Wetmore
 */
final class SSLEngineImpl extends SSLEngine implements SSLTransport {
    private final SSLContextImpl        sslContext;
    final TransportContext              conContext;

    /**
     * Constructor for an SSLEngine from SSLContext, without
     * host/port hints.
     *
     * This Engine will not be able to cache sessions, but must renegotiate
     * everything by hand.
     */
    SSLEngineImpl(SSLContextImpl sslContext) {
        this(sslContext, null, -1);
    }

    /**
     * Constructor for an SSLEngine from SSLContext.
     */
    SSLEngineImpl(SSLContextImpl sslContext,
            String host, int port) {
        super(host, port);
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        if (sslContext.isDTLS()) {
            this.conContext = new TransportContext(sslContext, this,
                    new DTLSInputRecord(handshakeHash),
                    new DTLSOutputRecord(handshakeHash));
        } else {
            this.conContext = new TransportContext(sslContext, this,
                    new SSLEngineInputRecord(handshakeHash),
                    new SSLEngineOutputRecord(handshakeHash));
        }

        // Server name indication is a connection scope extension.
        if (host != null) {
            this.conContext.sslConfig.serverNames =
                    Utilities.addToSNIServerNameList(
                            conContext.sslConfig.serverNames, host);
        }
    }

    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (conContext.isUnsureMode) {
            throw new IllegalStateException(
                    "Client/Server mode has not yet been set.");
        }

        try {
            conContext.kickstart();
        } catch (IOException ioe) {
            conContext.fatal(Alert.HANDSHAKE_FAILURE,
                "Couldn't kickstart handshaking", ioe);
        } catch (Exception ex) {     // including RuntimeException
            conContext.fatal(Alert.INTERNAL_ERROR,
                "Fail to begin handshake", ex);
        }
    }

    @Override
    public synchronized SSLEngineResult wrap(ByteBuffer[] appData,
            int offset, int length, ByteBuffer netData) throws SSLException {
        return wrap(
                appData, offset, length, new ByteBuffer[]{ netData }, 0, 1);
    }

    // @Override
    public synchronized SSLEngineResult wrap(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength,
        ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws SSLException {

        if (conContext.isUnsureMode) {
            throw new IllegalStateException(
                    "Client/Server mode has not yet been set.");
        }

        // See if the handshaker needs to report back some SSLException.
        if (conContext.outputRecord.isEmpty()) {
            checkTaskThrown();
        }   // Otherwise, deliver cached records before throwing task exception.

        // check parameters
        checkParams(srcs, srcsOffset, srcsLength, dsts, dstsOffset, dstsLength);

        try {
            return writeRecord(
                srcs, srcsOffset, srcsLength, dsts, dstsOffset, dstsLength);
        } catch (SSLProtocolException spe) {
            // may be an unexpected handshake message
            conContext.fatal(Alert.UNEXPECTED_MESSAGE, spe);
        } catch (IOException ioe) {
            conContext.fatal(Alert.INTERNAL_ERROR,
                "problem wrapping app data", ioe);
        } catch (Exception ex) {     // including RuntimeException
            conContext.fatal(Alert.INTERNAL_ERROR,
                "Fail to wrap application data", ex);
        }

        return null;    // make compiler happy
    }

    private SSLEngineResult writeRecord(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength,
        ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws IOException {

        if (isOutboundDone()) {
            return new SSLEngineResult(
                    Status.CLOSED, getHandshakeStatus(), 0, 0);
        }

        HandshakeContext hc = conContext.handshakeContext;
        HandshakeStatus hsStatus = null;
        if (!conContext.isNegotiated &&
                !conContext.isClosed() && !conContext.isBroken) {
            conContext.kickstart();

            hsStatus = getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_UNWRAP) {
                /*
                 * For DTLS, if the handshake state is
                 * HandshakeStatus.NEED_UNWRAP, a call to SSLEngine.wrap()
                 * means that the previous handshake packets (if delivered)
                 * get lost, and need retransmit the handshake messages.
                 */
                if (!sslContext.isDTLS() || hc == null ||
                        !hc.sslConfig.enableRetransmissions ||
                        conContext.outputRecord.firstMessage) {

                    return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
                }   // otherwise, need retransmission
            }
        }

        if (hsStatus == null) {
            hsStatus = getHandshakeStatus();
        }

        /*
         * If we have a task outstanding, this *MUST* be done before
         * doing any more wrapping, because we could be in the middle
         * of receiving a handshake message, for example, a finished
         * message which would change the ciphers.
         */
        if (hsStatus == HandshakeStatus.NEED_TASK) {
            return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
        }

        int dstsRemains = 0;
        for (int i = dstsOffset; i < dstsOffset + dstsLength; i++) {
            dstsRemains += dsts[i].remaining();
        }

        // Check destination buffer size.
        //
        // We can be smarter about using smaller buffer sizes later.  For
        // now, force it to be large enough to handle any valid record.
        if (dstsRemains < conContext.conSession.getPacketBufferSize()) {
            return new SSLEngineResult(
                Status.BUFFER_OVERFLOW, getHandshakeStatus(), 0, 0);
        }

        int srcsRemains = 0;
        for (int i = srcsOffset; i < srcsOffset + srcsLength; i++) {
            srcsRemains += srcs[i].remaining();
        }

        Ciphertext ciphertext = null;
        try {
            // Acquire the buffered to-be-delivered records or retransmissions.
            //
            // May have buffered records, or need retransmission if handshaking.
            if (!conContext.outputRecord.isEmpty() || (hc != null &&
                    hc.sslConfig.enableRetransmissions &&
                    hc.sslContext.isDTLS() &&
                    hsStatus == HandshakeStatus.NEED_UNWRAP)) {
                ciphertext = encode(null, 0, 0,
                        dsts, dstsOffset, dstsLength);
            }

            if (ciphertext == null && srcsRemains != 0) {
                ciphertext = encode(srcs, srcsOffset, srcsLength,
                        dsts, dstsOffset, dstsLength);
            }
        } catch (IOException ioe) {
            if (ioe instanceof SSLException) {
                throw ioe;
            } else {
                throw new SSLException("Write problems", ioe);
            }
        }

        /*
         * Check for status.
         */
        Status status = (isOutboundDone() ? Status.CLOSED : Status.OK);
        if (ciphertext != null && ciphertext.handshakeStatus != null) {
            hsStatus = ciphertext.handshakeStatus;
        } else {
            hsStatus = getHandshakeStatus();
        }

        int deltaSrcs = srcsRemains;
        for (int i = srcsOffset; i < srcsOffset + srcsLength; i++) {
            deltaSrcs -= srcs[i].remaining();
        }

        int deltaDsts = dstsRemains;
        for (int i = dstsOffset; i < dstsOffset + dstsLength; i++) {
            deltaDsts -= dsts[i].remaining();
        }

        return new SSLEngineResult(status, hsStatus, deltaSrcs, deltaDsts,
                ciphertext != null ? ciphertext.recordSN : -1L);
    }

    private Ciphertext encode(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength,
        ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws IOException {

        Ciphertext ciphertext = null;
        try {
            ciphertext = conContext.outputRecord.encode(
                srcs, srcsOffset, srcsLength, dsts, dstsOffset, dstsLength);
        } catch (SSLHandshakeException she) {
            // may be record sequence number overflow
            conContext.fatal(Alert.HANDSHAKE_FAILURE, she);
        } catch (IOException e) {
            conContext.fatal(Alert.UNEXPECTED_MESSAGE, e);
        }

        if (ciphertext == null) {
            return Ciphertext.CIPHERTEXT_NULL;
        }

        // Is the handshake completed?
        boolean needRetransmission =
                conContext.sslContext.isDTLS() &&
                conContext.getHandshakeContext(TransportContext.PRE) != null &&
                conContext.handshakeContext.sslConfig.enableRetransmissions;
        HandshakeStatus hsStatus =
                tryToFinishHandshake(ciphertext.contentType);
        if (needRetransmission &&
                hsStatus == HandshakeStatus.FINISHED &&
                conContext.sslContext.isDTLS() &&
                ciphertext.handshakeType == SSLHandshake.FINISHED.id) {
            // Retransmit the last flight for DTLS.
            //
            // The application data transactions may begin immediately
            // after the last flight.  If the last flight get lost, the
            // application data may be discarded accordingly.  As could
            // be an issue for some applications.  This impact can be
            // mitigated by sending the last fligth twice.
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,verbose")) {
                SSLLogger.finest("retransmit the last flight messages");
            }

            conContext.outputRecord.launchRetransmission();
            hsStatus = HandshakeStatus.NEED_WRAP;
        }

        if (hsStatus == null) {
            hsStatus = conContext.getHandshakeStatus();
        }

        // Is the sequence number is nearly overflow?
        if (conContext.outputRecord.seqNumIsHuge()) {
            hsStatus = tryKeyUpdate(hsStatus);
        }

        // update context status
        ciphertext.handshakeStatus = hsStatus;

        return ciphertext;
    }

    private HandshakeStatus tryToFinishHandshake(byte contentType) {
        HandshakeStatus hsStatus = null;
        if ((contentType == ContentType.HANDSHAKE.id) &&
                conContext.outputRecord.isEmpty()) {
            if (conContext.handshakeContext == null) {
                hsStatus = HandshakeStatus.FINISHED;
            } else if (conContext.getHandshakeContext(TransportContext.POST) != null) {
                return null;
            } else if (conContext.handshakeContext.handshakeFinished) {
                hsStatus = conContext.finishHandshake();
            }
        }   // Otherwise, the followed call to getHSStatus() will help.

        return hsStatus;
    }

    /**
     * Try renegotiation or key update for sequence number wrap.
     *
     * Note that in order to maintain the handshake status properly, we check
     * the sequence number after the last record reading/writing process.  As
     * we request renegotiation or close the connection for wrapped sequence
     * number when there is enough sequence number space left to handle a few
     * more records, so the sequence number of the last record cannot be
     * wrapped.
     */
    private HandshakeStatus tryKeyUpdate(
            HandshakeStatus currentHandshakeStatus) throws IOException {
        // Don't bother to kickstart the renegotiation or key update when the
        // local is asking for it.
        if ((conContext.handshakeContext == null) &&
                !conContext.isClosed() && !conContext.isBroken) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest("key update to wrap sequence number");
            }
            conContext.keyUpdate();
            return conContext.getHandshakeStatus();
        }

        return currentHandshakeStatus;
    }

    private static void checkParams(
            ByteBuffer[] srcs, int srcsOffset, int srcsLength,
            ByteBuffer[] dsts, int dstsOffset, int dstsLength) {

        if ((srcs == null) || (dsts == null)) {
            throw new IllegalArgumentException(
                    "source or destination buffer is null");
        }

        if ((dstsOffset < 0) || (dstsLength < 0) ||
                (dstsOffset > dsts.length - dstsLength)) {
            throw new IndexOutOfBoundsException(
                    "index out of bound of the destination buffers");
        }

        if ((srcsOffset < 0) || (srcsLength < 0) ||
                (srcsOffset > srcs.length - srcsLength)) {
            throw new IndexOutOfBoundsException(
                    "index out of bound of the source buffers");
        }

        for (int i = dstsOffset; i < dstsOffset + dstsLength; i++) {
            if (dsts[i] == null) {
                throw new IllegalArgumentException(
                        "destination buffer[" + i + "] == null");
            }

            /*
             * Make sure the destination bufffers are writable.
             */
            if (dsts[i].isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
        }

        for (int i = srcsOffset; i < srcsOffset + srcsLength; i++) {
            if (srcs[i] == null) {
                throw new IllegalArgumentException(
                        "source buffer[" + i + "] == null");
            }
        }
    }

    @Override
    public synchronized SSLEngineResult unwrap(ByteBuffer src,
            ByteBuffer[] dsts, int offset, int length) throws SSLException {
        return unwrap(
                new ByteBuffer[]{src}, 0, 1, dsts, offset, length);
    }

    // @Override
    public synchronized SSLEngineResult unwrap(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength,
        ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws SSLException {

        if (conContext.isUnsureMode) {
            throw new IllegalStateException(
                    "Client/Server mode has not yet been set.");
        }

        // See if the handshaker needs to report back some SSLException.
        checkTaskThrown();

        // check parameters
        checkParams(srcs, srcsOffset, srcsLength, dsts, dstsOffset, dstsLength);

        try {
            return readRecord(
                srcs, srcsOffset, srcsLength, dsts, dstsOffset, dstsLength);
        } catch (SSLProtocolException spe) {
            // may be an unexpected handshake message
            conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    spe.getMessage(), spe);
        } catch (IOException ioe) {
            /*
             * Don't reset position so it looks like we didn't
             * consume anything.  We did consume something, and it
             * got us into this situation, so report that much back.
             * Our days of consuming are now over anyway.
             */
            conContext.fatal(Alert.INTERNAL_ERROR,
                    "problem unwrapping net record", ioe);
        } catch (Exception ex) {     // including RuntimeException
            conContext.fatal(Alert.INTERNAL_ERROR,
                "Fail to unwrap network record", ex);
        }

        return null;    // make compiler happy
    }

    private SSLEngineResult readRecord(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength,
        ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws IOException {

        /*
         * Check if we are closing/closed.
         */
        if (isInboundDone()) {
            return new SSLEngineResult(
                    Status.CLOSED, getHandshakeStatus(), 0, 0);
        }

        HandshakeStatus hsStatus = null;
        if (!conContext.isNegotiated &&
                !conContext.isClosed() && !conContext.isBroken) {
            conContext.kickstart();

            /*
             * If there's still outbound data to flush, we
             * can return without trying to unwrap anything.
             */
            hsStatus = getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_WRAP) {
                return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
            }
        }

        if (hsStatus == null) {
            hsStatus = getHandshakeStatus();
        }

        /*
         * If we have a task outstanding, this *MUST* be done before
         * doing any more unwrapping, because we could be in the middle
         * of receiving a handshake message, for example, a finished
         * message which would change the ciphers.
         */
        if (hsStatus == HandshakeStatus.NEED_TASK) {
            return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
        }

        if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {
            Plaintext plainText = null;
            try {
                plainText = decode(null, 0, 0,
                        dsts, dstsOffset, dstsLength);
            } catch (IOException ioe) {
                if (ioe instanceof SSLException) {
                    throw ioe;
                } else {
                    throw new SSLException("readRecord", ioe);
                }
            }

            Status status = (isInboundDone() ? Status.CLOSED : Status.OK);
            if (plainText.handshakeStatus != null) {
                hsStatus = plainText.handshakeStatus;
            } else {
                hsStatus = getHandshakeStatus();
            }

            return new SSLEngineResult(
                    status, hsStatus, 0, 0, plainText.recordSN);
        }

        int srcsRemains = 0;
        for (int i = srcsOffset; i < srcsOffset + srcsLength; i++) {
            srcsRemains += srcs[i].remaining();
        }

        if (srcsRemains == 0) {
            return new SSLEngineResult(Status.OK, getHandshakeStatus(), 0, 0);
        }

        /*
         * Check the packet to make sure enough is here.
         * This will also indirectly check for 0 len packets.
         */
        int packetLen = 0;
        try {
            packetLen = conContext.inputRecord.bytesInCompletePacket(
                    srcs, srcsOffset, srcsLength);
        } catch (SSLException ssle) {
            // Need to discard invalid records for DTLS protocols.
            if (sslContext.isDTLS()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,verbose")) {
                    SSLLogger.finest("Discard invalid DTLS records", ssle);
                }

                // invalid, discard the entire data [section 4.1.2.7, RFC 6347]
                int deltaNet = 0;
                // int deltaNet = netData.remaining();
                // netData.position(netData.limit());

                Status status = (isInboundDone() ? Status.CLOSED : Status.OK);
                if (hsStatus == null) {
                    hsStatus = getHandshakeStatus();
                }

                return new SSLEngineResult(status, hsStatus, deltaNet, 0, -1L);
            } else {
                throw ssle;
            }
        }

        // Is this packet bigger than SSL/TLS normally allows?
        if (packetLen > conContext.conSession.getPacketBufferSize()) {
            int largestRecordSize = sslContext.isDTLS() ?
                    DTLSRecord.maxRecordSize : SSLRecord.maxLargeRecordSize;
            if ((packetLen <= largestRecordSize) && !sslContext.isDTLS()) {
                // Expand the expected maximum packet/application buffer
                // sizes.
                //
                // Only apply to SSL/TLS protocols.

                // Old behavior: shall we honor the System Property
                // "jsse.SSLEngine.acceptLargeFragments" if it is "false"?
                conContext.conSession.expandBufferSizes();
            }

            // check the packet again
            largestRecordSize = conContext.conSession.getPacketBufferSize();
            if (packetLen > largestRecordSize) {
                throw new SSLProtocolException(
                        "Input record too big: max = " +
                        largestRecordSize + " len = " + packetLen);
            }
        }

        /*
         * Check for OVERFLOW.
         *
         * Delay enforcing the application buffer free space requirement
         * until after the initial handshaking.
         */
        int dstsRemains = 0;
        for (int i = dstsOffset; i < dstsOffset + dstsLength; i++) {
            dstsRemains += dsts[i].remaining();
        }

        if (conContext.isNegotiated) {
            int FragLen =
                    conContext.inputRecord.estimateFragmentSize(packetLen);
            if (FragLen > dstsRemains) {
                return new SSLEngineResult(
                        Status.BUFFER_OVERFLOW, hsStatus, 0, 0);
            }
        }

        // check for UNDERFLOW.
        if ((packetLen == -1) || (srcsRemains < packetLen)) {
            return new SSLEngineResult(Status.BUFFER_UNDERFLOW, hsStatus, 0, 0);
        }

        /*
         * We're now ready to actually do the read.
         */
        Plaintext plainText = null;
        try {
            plainText = decode(srcs, srcsOffset, srcsLength,
                            dsts, dstsOffset, dstsLength);
        } catch (IOException ioe) {
            if (ioe instanceof SSLException) {
                throw ioe;
            } else {
                throw new SSLException("readRecord", ioe);
            }
        }

        /*
         * Check the various condition that we could be reporting.
         *
         * It's *possible* something might have happened between the
         * above and now, but it was better to minimally lock "this"
         * during the read process.  We'll return the current
         * status, which is more representative of the current state.
         *
         * status above should cover:  FINISHED, NEED_TASK
         */
        Status status = (isInboundDone() ? Status.CLOSED : Status.OK);
        if (plainText.handshakeStatus != null) {
            hsStatus = plainText.handshakeStatus;
        } else {
            hsStatus = getHandshakeStatus();
        }

        int deltaNet = srcsRemains;
        for (int i = srcsOffset; i < srcsOffset + srcsLength; i++) {
            deltaNet -= srcs[i].remaining();
        }

        int deltaApp = dstsRemains;
        for (int i = dstsOffset; i < dstsOffset + dstsLength; i++) {
            deltaApp -= dsts[i].remaining();
        }

        return new SSLEngineResult(
                status, hsStatus, deltaNet, deltaApp, plainText.recordSN);
    }

    private Plaintext decode(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength,
        ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws IOException {

        Plaintext pt = SSLTransport.decode(conContext,
                            srcs, srcsOffset, srcsLength,
                            dsts, dstsOffset, dstsLength);

        // Is the handshake completed?
        if (pt != Plaintext.PLAINTEXT_NULL) {
            HandshakeStatus hsStatus = tryToFinishHandshake(pt.contentType);
            if (hsStatus == null) {
                pt.handshakeStatus = conContext.getHandshakeStatus();
            } else {
                pt.handshakeStatus = hsStatus;
            }

            // Is the sequence number is nearly overflow?
            if (conContext.inputRecord.seqNumIsHuge()) {
                pt.handshakeStatus =
                        tryKeyUpdate(pt.handshakeStatus);
            }
        }

        return pt;
    }

    @Override
    public synchronized Runnable getDelegatedTask() {
        if (conContext.handshakeContext != null && // PRE or POST handshake
                !conContext.handshakeContext.taskDelegated &&
                !conContext.handshakeContext.delegatedActions.isEmpty()) {
            conContext.handshakeContext.taskDelegated = true;
            return new DelegatedTask(this);
        }

        return null;
    }

    @Override
    public synchronized void closeInbound() throws SSLException {
        conContext.closeInbound();
    }

    @Override
    public synchronized boolean isInboundDone() {
        return conContext.isInboundDone();
    }

    @Override
    public synchronized void closeOutbound() {
        conContext.closeOutbound();
    }

    @Override
    public synchronized boolean isOutboundDone() {
        return conContext.isOutboundDone();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return CipherSuite.namesOf(sslContext.getSupportedCipherSuites());
    }

    @Override
    public synchronized String[] getEnabledCipherSuites() {
        return CipherSuite.namesOf(conContext.sslConfig.enabledCipherSuites);
    }

    @Override
    public synchronized void setEnabledCipherSuites(String[] suites) {
        if (suites == null) {
            throw new IllegalArgumentException("CipherSuites cannot be null");
        }

        conContext.sslConfig.enabledCipherSuites =
                CipherSuite.validValuesOf(suites);
    }

    @Override
    public String[] getSupportedProtocols() {
        return ProtocolVersion.toStringArray(
                sslContext.getSupportedProtocolVersions());
    }

    @Override
    public synchronized String[] getEnabledProtocols() {
        return ProtocolVersion.toStringArray(
                conContext.sslConfig.enabledProtocols);
    }

    @Override
    public synchronized void setEnabledProtocols(String[] protocols) {
        if (protocols == null) {
            throw new IllegalArgumentException("Protocols cannot be null");
        }

        conContext.sslConfig.enabledProtocols =
                ProtocolVersion.namesOf(protocols);
    }

    @Override
    public synchronized SSLSession getSession() {
        return conContext.conSession;
    }

    @Override
    public synchronized SSLSession getHandshakeSession() {
        return conContext.handshakeContext == null ?
                null : conContext.handshakeContext.handshakeSession;
    }

    @Override
    public synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return conContext.getHandshakeStatus();
    }

    @Override
    public synchronized void setUseClientMode(boolean mode) {
        conContext.setUseClientMode(mode);
    }

    @Override
    public synchronized boolean getUseClientMode() {
        return conContext.sslConfig.isClientMode;
    }

    @Override
    public synchronized void setNeedClientAuth(boolean need) {
        conContext.sslConfig.clientAuthType =
                (need ? ClientAuthType.CLIENT_AUTH_REQUIRED :
                        ClientAuthType.CLIENT_AUTH_NONE);
    }

    @Override
    public synchronized boolean getNeedClientAuth() {
        return (conContext.sslConfig.clientAuthType ==
                        ClientAuthType.CLIENT_AUTH_REQUIRED);
    }

    @Override
    public synchronized void setWantClientAuth(boolean want) {
        conContext.sslConfig.clientAuthType =
                (want ? ClientAuthType.CLIENT_AUTH_REQUESTED :
                        ClientAuthType.CLIENT_AUTH_NONE);
    }

    @Override
    public synchronized boolean getWantClientAuth() {
        return (conContext.sslConfig.clientAuthType ==
                        ClientAuthType.CLIENT_AUTH_REQUESTED);
    }

    @Override
    public synchronized void setEnableSessionCreation(boolean flag) {
        conContext.sslConfig.enableSessionCreation = flag;
    }

    @Override
    public synchronized boolean getEnableSessionCreation() {
        return conContext.sslConfig.enableSessionCreation;
    }

    @Override
    public synchronized SSLParameters getSSLParameters() {
        return conContext.sslConfig.getSSLParameters();
    }

    @Override
    public synchronized void setSSLParameters(SSLParameters params) {
        conContext.sslConfig.setSSLParameters(params);

        if (conContext.sslConfig.maximumPacketSize != 0) {
            conContext.outputRecord.changePacketSize(
                    conContext.sslConfig.maximumPacketSize);
        }
    }

    @Override
    public synchronized String getApplicationProtocol() {
        return conContext.applicationProtocol;
    }

    @Override
    public synchronized String getHandshakeApplicationProtocol() {
        return conContext.handshakeContext == null ?
                null : conContext.handshakeContext.applicationProtocol;
    }

    @Override
    public synchronized void setHandshakeApplicationProtocolSelector(
            BiFunction<SSLEngine, List<String>, String> selector) {
        conContext.sslConfig.engineAPSelector = selector;
    }

    @Override
    public synchronized BiFunction<SSLEngine, List<String>, String>
            getHandshakeApplicationProtocolSelector() {
        return conContext.sslConfig.engineAPSelector;
    }

    @Override
    public boolean useDelegatedTask() {
        return true;
    }

    private synchronized void checkTaskThrown() throws SSLException {
        HandshakeContext hc = conContext.handshakeContext;
        if (hc != null && hc.delegatedThrown != null) {
            try {
                throw getTaskThrown(hc.delegatedThrown);
            } finally {
                hc.delegatedThrown = null;
            }
        }

        if (conContext.isBroken && conContext.closeReason != null) {
            throw getTaskThrown(conContext.closeReason);
        }
    }

    private static SSLException getTaskThrown(Exception taskThrown) {
        String msg = taskThrown.getMessage();

        if (msg == null) {
            msg = "Delegated task threw Exception or Error";
        }

        if (taskThrown instanceof RuntimeException) {
            throw new RuntimeException(msg, taskThrown);
        } else if (taskThrown instanceof SSLHandshakeException) {
            return (SSLHandshakeException)
                new SSLHandshakeException(msg).initCause(taskThrown);
        } else if (taskThrown instanceof SSLKeyException) {
            return (SSLKeyException)
                new SSLKeyException(msg).initCause(taskThrown);
        } else if (taskThrown instanceof SSLPeerUnverifiedException) {
            return (SSLPeerUnverifiedException)
                new SSLPeerUnverifiedException(msg).initCause(taskThrown);
        } else if (taskThrown instanceof SSLProtocolException) {
            return (SSLProtocolException)
                new SSLProtocolException(msg).initCause(taskThrown);
        } else if (taskThrown instanceof SSLException) {
            return (SSLException)taskThrown;
        } else {
            return new SSLException(msg, taskThrown);
        }
    }

    /**
     * Implement a simple task delegator.
     */
    private static class DelegatedTask implements Runnable {
        private final SSLEngineImpl engine;

        DelegatedTask(SSLEngineImpl engineInstance) {
            this.engine = engineInstance;
        }

        @Override
        public void run() {
            synchronized (engine) {
                HandshakeContext hc = engine.conContext.handshakeContext;
                if (hc == null || hc.delegatedActions.isEmpty()) {
                    return;
                }

                try {
                    AccessController.doPrivileged(
                            new DelegatedAction(hc), engine.conContext.acc);
                } catch (PrivilegedActionException pae) {
                    // Get the handshake context again in case the
                    // handshaking has completed.
                    hc = engine.conContext.handshakeContext;
                    if (hc != null) {
                        hc.delegatedThrown = pae.getException();
                    } else if (engine.conContext.closeReason != null) {
                        engine.conContext.closeReason =
                                getTaskThrown(pae.getException());
                    }
                } catch (RuntimeException rte) {
                    // Get the handshake context again in case the
                    // handshaking has completed.
                    hc = engine.conContext.handshakeContext;
                    if (hc != null) {
                        hc.delegatedThrown = rte;
                    } else if (engine.conContext.closeReason != null) {
                        engine.conContext.closeReason = rte;
                    }
                }

                // Get the handshake context again in case the
                // handshaking has completed.
                hc = engine.conContext.handshakeContext;
                if (hc != null) {
                    hc.taskDelegated = false;
                }
            }
        }

        private static class DelegatedAction
                implements PrivilegedExceptionAction<Void> {
            final HandshakeContext context;
            DelegatedAction(HandshakeContext context) {
                this.context = context;
            }

            @Override
            public Void run() throws Exception {
                while (!context.delegatedActions.isEmpty()) {
                    // Report back the task SSLException
                    if (context.delegatedThrown != null) {
                        Exception delegatedThrown = context.delegatedThrown;
                        context.delegatedThrown = null;
                        throw getTaskThrown(delegatedThrown);
                    }

                    Map.Entry<Byte, ByteBuffer> me =
                            context.delegatedActions.poll();
                    if (me != null) {
                        context.dispatch(me.getKey(), me.getValue());
                    }
                }
                return null;
            }
        }
    }
}
