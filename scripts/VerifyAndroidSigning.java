import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

/** Checks the release key in memory before starting a build; never prints passwords. */
public class VerifyAndroidSigning {
    public static void main(String[] args) {
        try {
            KeyStore store = KeyStore.getInstance(
                new File(System.getenv("ANDROID_KEYSTORE_PATH")),
                System.getenv("ANDROID_KEYSTORE_PASSWORD").toCharArray());
            String alias = System.getenv("ANDROID_KEY_ALIAS");
            X509Certificate certificate = (X509Certificate) store.getCertificate(alias);
            if (certificate == null) throw new IllegalArgumentException("Missing signing certificate");
            String fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            if (!fingerprint.equalsIgnoreCase(args[0].replace(":", ""))) {
                throw new SecurityException("签名证书与现有正式版本不一致。");
            }
            PrivateKey key = (PrivateKey) store.getKey(
                alias, System.getenv("ANDROID_KEY_PASSWORD").toCharArray());
            if (key == null) throw new IllegalArgumentException("Missing private key");
            String algorithm = switch (key.getAlgorithm()) {
                case "RSA" -> "SHA256withRSA";
                case "EC" -> "SHA256withECDSA";
                default -> throw new IllegalArgumentException("Unsupported signing key");
            };
            Signature verifier = Signature.getInstance(algorithm);
            byte[] message = "Ksuser release signing preflight".getBytes(StandardCharsets.UTF_8);
            verifier.initSign(key);
            verifier.update(message);
            byte[] signed = verifier.sign();
            verifier.initVerify(certificate);
            verifier.update(message);
            if (!verifier.verify(signed)) throw new SecurityException("私钥与证书不匹配。");
            System.out.println("签名密钥验证通过，别名：" + alias);
        } catch (Exception error) {
            System.err.println("签名预检失败（" + error.getClass().getSimpleName() + "）。"
                + "请确认密钥库、别名、密钥库密码和私钥密码；密码不会被输出。");
            if (error instanceof SecurityException) System.err.println(error.getMessage());
            System.exit(1);
        }
    }
}
