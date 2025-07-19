package com.atlasmind.ai_travel_recommendation.service;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    @Value("${security.jwt.expirationTime}")
    private long expirationTime;

    public Long getExpirationTime() {
        return expirationTime;
    }
    // UserDetails is like a profile card of the user that is logged in containing info about the user.
    // JWT has - Header, Payload, Signature ( ((hash) header + payload) + (signed) private key ).
    public String generateToken(UserDetails userDetails)throws NoSuchAlgorithmException, InvalidKeySpecException {
        return generateToken(new HashMap<>(), userDetails);
    }

    // Object because it allows multiple types in the payload
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt((new Date(System.currentTimeMillis())))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith((getPrivateKey()))
                .compact();
    }
    // Parser does the job of splitting the token and verifying the signature and then returning the claims.
    private Claims extractAllClaims(String jwtToken) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return Jwts
                .parser() // initializes the parser.
                .verifyWith(getPublicKey()) // This key is used to verify the signature of the JWT Token.
                .build() // Build the parser
                .parseSignedClaims(jwtToken) // Verify the signature of the Jwt Token.
                .getPayload(); // Get the payload of the returned Jws<Claims> (JWT Token) Object.
    }

    // Need to define what T is, need to specify that T is a generic type (String, Date, etc.) which is why <T> is declared!!
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) throws NoSuchAlgorithmException, InvalidKeySpecException {
        final Claims claim = extractAllClaims(token);
        return claimsResolver.apply(claim);
    }

    public String extractUsername(String jwtToken) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return extractClaim(jwtToken, Claims::getSubject); //need to specify the type of object it needs to be called on, which is why Claims is necessary!!
    }

    private Date extractExpiration(String token) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) throws NoSuchAlgorithmException, InvalidKeySpecException {
        final String userEmail = extractUsername(token);
        return (userEmail.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return extractExpiration(token).before(new Date());
    }

    public PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        Dotenv dotenv = Dotenv.load();
        String publicKey = dotenv.get("PUBLIC_KEY");
        publicKey = publicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""); // Removes spaces, newlines, tabs, etc
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes); // encoding of a public key
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private PrivateKey getPrivateKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        Dotenv dotenv = Dotenv.load();
        String privateKey = dotenv.get("PRIVATE_KEY");
//        System.out.println("PRIVATE_KEY raw: " + System.getenv("PRIVATE_KEY"));
        privateKey = privateKey.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }
}
