package com.vmware.vcfa.util;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class ConfigReader {

    Map<String, String> serverconfig;

    public ConfigReader() {
        String fileName = "application.yaml";  // File is under src/main/resources
        InputStream inputStream = ConfigReader.class.getClassLoader().getResourceAsStream(fileName);
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            serverconfig = (Map<String, String>) data.get("server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getAccessToken() {
        String acces_token = serverconfig.get("access_token");
        boolean verifySsl = getVerifySsl();
        if (acces_token.equals("null")) {
            try {
                acces_token = getAccessTokenWithSelfSignedCert(getServerUrl(), serverconfig.get("username"), serverconfig.get("password"), serverconfig.get("organization"), verifySsl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return acces_token;
    }

    public String getAccessTokenWithSelfSignedCert(
            String tmUrl,
            String username,
            String password,
            String orgName,
            boolean verifySSL) throws Exception {

        if (verifySSL) {
            // Load trusted cert from InputStream
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream sslCaCert = new FileInputStream(getSslCertPath());
            X509Certificate cert = (X509Certificate) cf.generateCertificate(sslCaCert);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ca", cert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } else {
            // Disable SSL verification completely (trust all certs + disable hostname check)
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Disable hostname verification
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        }

        // Send POST request with basic auth
        String urlStr = tmUrl + "/tm/cloudapi/1.0.0/sessions";
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        return getToken(username, password, orgName, conn);
    }


    private static String getToken(String username, String password, String orgName, HttpURLConnection conn) throws IOException {
        String auth = String.format("%s@%s", username, orgName) + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        conn.setRequestProperty("Accept", "*/*;version=41.0.0-alpha");

        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(new byte[0]);
        }

        String token = conn.getHeaderField("x-vmware-vcloud-access-token");
        conn.disconnect();
        return token;
    }


    public String getServerUrl() {
        return serverconfig.get("url");
    }

    public String getSslCertPath() {
        return serverconfig.get("ssl_cert_path");
    }

    public Boolean getVerifySsl() {
        Object value = serverconfig.get("verify_ssl");

        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        } else {
            return false; // Default value if key is missing or invalid
        }
    }

}
