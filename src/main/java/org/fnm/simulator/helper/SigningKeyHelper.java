package org.fnm.simulator.helper;

import com.nimbusds.jose.jwk.JWK;

import java.text.ParseException;

public class SigningKeyHelper {

    // EC Key pair
    private static final String ecKeyPair = """
            {
              "kty": "EC",
              "d": "P6zvae6b76NlMDYB1XP-ufSmNCLpQhDETSeqloBVGWQ",
              "use": "sig",
              "crv": "P-256",
              "kid": "ec-signing-key",
              "x": "cB7hNaUvCNpjKbhEDPiZONcT2WP7Na0M2Vghdt_f0SU",
              "y": "BqY4QI6UCXqpFy5PHpcljA61QeFexLF180eYxheTnlU",
              "alg": "ES256"
            }""";

    // RSA Key pair
    private static final String rsaKeyPair = """
            {
              "p": "04t3BfwkH_GuxjMD_A8v0XSWwgszYK9PgbQxRaHGDDXdbSiB5K3dr9Bdm3BXI-Eer1Ue6W0QQ2aN5ptxd7YUOHC7icKzilPk--7qChsuXb8qfQwt7Y2q1FDqWEigy6Oc2N2MCFXu29UUJBl8dUJdU6jX9_zSVtrgv6syy8ELx9k",
              "kty": "RSA",
              "q": "_dqsrRrs8v6HrZUaUhZPU0HXRmJ5wlHfmfYH2tFkm4J3i68HWl2w8y3AEk2tsoR3XirkjmtafSndZGmiLoYe6z-0btWyqwV7ZN3EZ0rWCIiu5PerpOctxF5VQwo7nFuJ7k_7Tyh0puskTa9A5hNdP6QxADLYifsH2H5rw83xV60",
              "d": "JOVQbRn7v-veg__UGfa0Vqn7OF_A5lkQzU1yify9n9YEkEJ6zhHFDlZmRREH0o4JzMOCAziaGLSgaIspwBI52JAIqzJbsS1bTQpngkPAIIrRq5Yo36jUjW2Nvg4pSMCaTyC04RyfaeB5aD4hDUba4UOCRGOXgYoDKspTx_R8kPP6AyCNc6zNH7ZO53zRsetuGmFqwnP1enmqfhGwCE67beGWG4V_EMr5cPr5hFPQUCBZrSg7L6_miQ2vRtPA0kYyfcxzugoYwUQo4MkRJkwW1DX0gfEh0HXAibfQ_3agvIsA0PjgMK1OmLPH05x5XqEYuGcRZZdt4uop7yu7KMZb4Q",
              "e": "AQAB",
              "use": "sig",
              "kid": "rsa-signing-key",
              "qi": "HC036ZumOtgIuZyqlmXWva9Dz5iql00eYtuBeWwOBLoNNUFkuVAY5v2nKVaLJComQWi6d6AeTJlXixRgmqHqBzMdmYzTGI-LQB5rpswy1y6-xxM7CXEutU1nGfdmMMdXv814xcKH0KswV62TwYrTmGEoMh5ouv34jXgIukJXF0Q",
              "dp": "rOnlqzWzTY0egBF5I1JCkuHKzOec4l3KDkn8sF8QZDCvQqeGrSZU-33E1eZYxBIiI8zfDIxCa-Vq_eF3ORPSqWO0V2GXoez7Uday5Pncx3IXx8jpzbArv4o4U1tiW7Hr5fQJn1KmgHhGE0_lxG8vm896YND-GVKlvOWyCoxMFdk",
              "alg": "RS256",
              "dq": "28WPrSYccZsOl6b4bIzIDEp6N4NV6ne7X61yy2xw73kaSiIz-XYXldDL8oyxjlXEihAUY4BpjObLtv0E3cZbyksSyMnVrt5GH-MnAE330teSosQRI-jI5LLOmgWn6gwN8_Xmmndp1oDIo0DQrN-i4VAi5AshEQP1nOaGIbSP9tk",
              "n": "0cWIFQS_1j03ioYCe5ZWbB6DINLsIBzw-Q3gftca6Fb6boim2BqKzpLQLDloN2KuZpbFJA70GlsJPhu5F72YFKbFPabrF11amSUUHR8UJlpwS9x57AFtEqKuxiTlb7rG2diDVqAMOdQ7n8gQEOBmhKOfnKKQyBcGIA7kfVocxSTRchmskNe_sHBXhxXH-k2vYTRLGJxSvaaGD6HX2XRtPGKnAVL7LoO8xqXUR_by9LbDBfPL4aKzcBDdzwV47hHjQvgk-rDvqownnGBPq-nRiIQjt58dAfNhTnLQUeXOEmDiUP06s4XlW5niynTYSJHDdeT29QhX3Bsdqqq3XinMpQ"
            }""";

    // Example public key
    private static final String exampleRSAPublicKey = """
            { "kty": "RSA",
              "e": "AQAB",
              "use": "sig",
              "kid": "jwt-signing-keys",
              "alg": "RS256","n":
              "0cWIFQS_1j03ioYCe5ZWbB6DINLsIBzw-Q3gftca6Fb6boim2BqKzpLQLDloN2KuZpbFJA70GlsJPhu5F72YFKbFPabrF11amSUUHR8UJlpwS9x57AFtEqKuxiTlb7rG2diDVqAMOdQ7n8gQEOBmhKOfnKKQyBcGIA7kfVocxSTRchmskNe_sHBXhxXH-k2vYTRLGJxSvaaGD6HX2XRtPGKnAVL7LoO8xqXUR_by9LbDBfPL4aKzcBDdzwV47hHjQvgk-rDvqownnGBPq-nRiIQjt58dAfNhTnLQUeXOEmDiUP06s4XlW5niynTYSJHDdeT29QhX3Bsdqqq3XinMpQ"
            }
            """;

    public static String getEcKeyPair() {
        return ecKeyPair;
    }

    public static JWK getEcKeyPairJWK() throws ParseException {
        return JWK.parse(ecKeyPair);
    }

    public static String getRsaKeyPair() {
        return rsaKeyPair;
    }

    public static JWK getRSAKeyPairJWK() throws ParseException {
        return JWK.parse(rsaKeyPair);
    }

    /**
     * RSA public key displayed as example in the sequence description.
     */
    public static String getExampleRSAPublicKey() {
        return exampleRSAPublicKey;
    }

}
