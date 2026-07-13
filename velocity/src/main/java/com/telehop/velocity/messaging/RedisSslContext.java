package com.telehop.velocity.messaging;

import com.telehop.common.db.RedisConfig;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * Builds the TLS socket factory and hostname verifier for Redis connections.
 *
 * <p>Three modes, driven by {@link RedisConfig}:</p>
 * <ul>
 *   <li>Verification on, no CA cert: system trust store validates the server.</li>
 *   <li>Verification on, CA cert set: only certificates signed by the supplied
 *       PEM CA (plus chain) are trusted. Used for self-signed deployments.</li>
 *   <li>Verification off: any certificate and hostname is accepted. Traffic is
 *       still encrypted, but the peer is not authenticated. Testing only.</li>
 * </ul>
 */
final class RedisSslContext {

    private final SSLSocketFactory socketFactory;
    private final HostnameVerifier hostnameVerifier;

    private RedisSslContext(SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier) {
        this.socketFactory = socketFactory;
        this.hostnameVerifier = hostnameVerifier;
    }

    SSLSocketFactory socketFactory() {
        return socketFactory;
    }

    /** May be null, in which case Jedis uses the default verifier. */
    HostnameVerifier hostnameVerifier() {
        return hostnameVerifier;
    }

    static RedisSslContext create(RedisConfig config) {
        try {
            if (!config.sslVerify()) {
                return new RedisSslContext(trustAllFactory(), (hostname, session) -> true);
            }
            if (!config.sslCaCert().isBlank()) {
                return new RedisSslContext(caCertFactory(Path.of(config.sslCaCert())), null);
            }
            // System trust store, default hostname verification.
            return new RedisSslContext((SSLSocketFactory) SSLSocketFactory.getDefault(), null);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to initialise Redis TLS: " + e.getMessage(), e);
        }
    }

    private static SSLSocketFactory caCertFactory(Path caCertPath) throws GeneralSecurityException, IOException {
        if (!Files.isReadable(caCertPath)) {
            throw new IOException("Redis CA certificate not readable: " + caCertPath);
        }

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs;
        try (InputStream in = Files.newInputStream(caCertPath)) {
            certs = certFactory.generateCertificates(in);
        }
        if (certs.isEmpty()) {
            throw new IOException("No certificates found in " + caCertPath);
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        int index = 0;
        for (Certificate cert : certs) {
            trustStore.setCertificateEntry("redis-ca-" + index++, cert);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return context.getSocketFactory();
    }

    private static SSLSocketFactory trustAllFactory() throws GeneralSecurityException {
        TrustManager trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustAll}, new SecureRandom());
        return context.getSocketFactory();
    }
}
