package com.subatomicplanets.shield;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;

public class KeyConverter {
    public static PublicKey getPublicKeyFromDer(byte[] derEncodedKey) throws Exception {
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(derEncodedKey)) {
            RSAPublicKey rsaPublicKey = RSAPublicKey.getInstance(asn1InputStream.readObject());
            BigInteger modulus = rsaPublicKey.getModulus();
            BigInteger exponent = rsaPublicKey.getPublicExponent();
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        }
    }

    public static byte[] getDerFromPublicKey(PublicKey publicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPublicKeySpec keySpec = keyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class);
        BigInteger modulus = keySpec.getModulus();
        BigInteger exponent = keySpec.getPublicExponent();
        RSAPublicKey rsaPublicKey = new RSAPublicKey(modulus, exponent);
        return rsaPublicKey.getEncoded();
    }
}