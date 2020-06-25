package io.tarantool.driver.auth;


import io.tarantool.driver.util.Assert;
import io.tarantool.driver.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * This authenticator performs authentication to the Tarantool server using default mechanism (chap-sha1) and
 * the provided plain user credentials.
 *
 * @author Alexey Kuzin
 */
public class ChapSha1TarantoolAuthenticator implements TarantoolAuthenticator<SimpleTarantoolCredentials> {

    /**
     * Basic constructor
     */
    public ChapSha1TarantoolAuthenticator() {
    }

    /**
     * Returns the supported {@link TarantoolAuthMechanism}
     * @return {@code TarantoolAuthMechanism.CHAPSHA1}
     */
    @Override
    public TarantoolAuthMechanism getMechanism() {
        return TarantoolAuthMechanism.CHAPSHA1;
    }

    /**
     * Check if the passed instance of {@link SimpleTarantoolCredentials} can be used for authentication
     * @param credentials Tarantool user credentials
     * @return true, if the username and password are not empty
     */
    @Override
    public boolean canAuthenticateWith(SimpleTarantoolCredentials credentials) {
        return StringUtils.hasText(credentials.getUsername()) && StringUtils.hasText(credentials.getPassword());
    }

    /**
     * Take the salt from the server connect response, write the authentication data based on the provided
     * {@link SimpleTarantoolCredentials}
     * See <a href="https://www.tarantool.io/en/doc/2.3/dev_guide/internals/box_protocol/#binary-protocol-authentication">
     *     https://www.tarantool.io/en/doc/2.3/dev_guide/internals/box_protocol/#binary-protocol-authentication</a>
     * @param serverAuthData the auth data responded by server to the connect request
     * @param credentials Tarantool user credentials
     * @return binary data for authentication request according to the chap-sha1 algorithm
     */
    @Override
    public byte[] prepareUserAuthData(byte[] serverAuthData,
                                      SimpleTarantoolCredentials credentials) throws TarantoolAuthenticationException {
        Assert.notNull(serverAuthData, "Server response must not be null");
        Assert.notNull(credentials, "Credentials must not be null");

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] auth = sha1.digest(credentials.getPassword().getBytes());
            byte[] salt = Base64.getDecoder().decode(serverAuthData);
            sha1.update(salt, 0, 20);
            sha1.update(sha1.digest(auth));
            byte[] scramble = sha1.digest();
            for (int i = 0, e = 20; i < e; i++) {
                auth[i] ^= scramble[i];
            }
            return auth;
        } catch (NoSuchAlgorithmException e) {
            throw new TarantoolAuthenticationException(e);
        }
    }
}
