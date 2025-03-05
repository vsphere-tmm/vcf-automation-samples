/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Utility for managing/downloading certs
 */
public class TmCertificateUtil {
    public static final int DEFAULT_PORT = 443;
    private static final int THIRTY_SECONDS =
            Long.valueOf(TimeUnit.SECONDS.toMillis(30)).intValue();

    protected Optional<X509Certificate[]> getCertificateForEndpoint(URI uri) {
        final String address = uri.getHost();
        final int uriPort = uri.getPort();
        final int port = uriPort == -1 ? DEFAULT_PORT : uriPort;

        try {
            final X509Certificate[] certificates = downloadCertificates(address, port, getProxy(uri));
            System.out.println("Successfully downloaded certificate chain "
                    + "from the secure endpoint at the specified host " + address + " and "
                    + port);
            return Optional.of(certificates);
        } catch (Exception e) {
            System.out.println("Error while downloading certificate chain "
                    + "from the secure endpoint at the specified host " + address + " and "
                    + port);
            return Optional.empty();
        }
    }

    /**
     * Download the certificate chain from the secure endpoint at the specified host and port.
     *
     * Temporarily accept any certificate provided as part of SSL Handshake. Initiate an SSL
     * connections and grab the certificate chain.
     * @return
     */
    public static X509Certificate[] downloadCertificates(final String host, final int port, final Proxy proxy)
            throws Exception {
        final SSLContext permissiveSslContext = getPermissiveSSLContext();
        final SSLSocketFactory sslSocketFactory = permissiveSslContext.getSocketFactory();
        if (proxy == null || Proxy.Type.DIRECT.equals(proxy.type())) {
            return getCertificatesDirectly(sslSocketFactory, host, port);
        } else {
            return getCertificatesThroughProxy(sslSocketFactory, host, port, proxy);
        }
    }

    private static X509Certificate[] getCertificatesDirectly(final SSLSocketFactory sslSocketFactory, final String host, final int port) throws Exception {
        try (final SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket()) {
            socket.setSoTimeout(THIRTY_SECONDS);
            socket.connect(new InetSocketAddress(host, port), THIRTY_SECONDS);

            return (X509Certificate[]) socket.getSession().getPeerCertificates();
        } finally {
            // Nothing to do. finally block to ensure socket is closed and compiler correctness
        }
    }

    private static X509Certificate[] getCertificatesThroughProxy(final SSLSocketFactory sslSocketFactory, final String host, final int port, final Proxy proxy)
            throws Exception {
        final InetSocketAddress socketAddress =  InetSocketAddress.createUnresolved(host, port);

        Socket socket = new Socket(proxy);
        socket.connect(socketAddress, THIRTY_SECONDS);

        final SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
        return (X509Certificate[]) sslSocket.getSession().getPeerCertificates();
    }

    private static SSLContext getPermissiveSSLContext() throws Exception {
        final SSLContext permissiveContext = SSLContext.getInstance("TLS");
        permissiveContext.init(null,
                new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                            throws CertificateException {
                        // Do nothing
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                            throws CertificateException {
                        // Do nothing
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }},
                new SecureRandom());

        return permissiveContext;
    }

    private static Proxy getProxy(URI uri) {
        final ProxySelector proxySelector = ProxySelector.getDefault();
        return proxySelector.select(uri).get(0);
    }

}
