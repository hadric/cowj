package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Spark;
import zoomba.lang.core.interpreter.ZMethodInterceptor;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A Basic Implementation for Authentication
 * Against our best interests - it might be important
 */
public interface Authenticator {

    /**
     * Logger for the Authenticator
     */
    Logger logger = LoggerFactory.getLogger(Authenticator.class);

    /**
     * Name for  UnAuthenticated
     */
    String UN_AUTHENTICATED = "UnAuthenticated" ;

    /**
     * Default attribute to set once authenticated
     */
    String USER_ID = "user-id" ;

    /**
     * A safe sandbox to call any method, failing which 401 response would be done
     * @param callable method must be wrapped around this
     * @return result of the method or, 401 response in case of any error raised
     * @param <R> return type of the method which has been sand-boxed
     */
    static  <R> R  safeAuthExecute(Callable<R> callable){
        try {
            return callable.call();
        }catch (Throwable t){
            logger.error("Error raised - will return 401 : " + t);
            Spark.halt(401, UN_AUTHENTICATED);
        }
        return null;
    }

    /**
     * A structure to gather User Information
     */
    interface UserInfo{
        /**
         * Identifier  for the user
         * @return user's id
         */
        String id();

        /**
         * User's token
         * @return token for the user
         */
        String token();

        /**
         * Expiry time of the token encoded in UTC milliseconds
         * @return expiry time of the token
         */
        long expiry();

        /**
         * Creates a concrete UserInfo
         * @param id identifier for the user
         * @param token authentication token for the user
         * @param expiry time for the token for the user
         * @return a concrete UserInfo implementation
         */
        static  UserInfo userInfo(String id, String token, long expiry){
            return new UserInfo() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public String token() {
                    return token;
                }

                @Override
                public long expiry() {
                    return expiry;
                }
            };
        }

        /**
         * A basal utility constant for Guest Access provision
         */
        UserInfo GUEST = UserInfo.userInfo( "", "", Long.MAX_VALUE);
    }

    /**
     * Creates a UserInfo object from request
     * @param request a spark.Request
     * @return  UserInfo
     * @throws Exception in case of any error
     */
    UserInfo userInfo(Request request) throws Exception ;

    /**
     * Tries to authenticate an user based on request
     * @param request a spark.Request
     * @return in case of successful authentication, returns user id
     */
    default String authenticate(Request request){
        UserInfo userInfo = safeAuthExecute( () -> userInfo(request));
        assert userInfo != null;
        // already expired...
        if ( userInfo.expiry() < System.currentTimeMillis() ){
            Spark.halt(401, UN_AUTHENTICATED);
        }
        request.attribute(USER_ID, userInfo.id());
        return userInfo.id();
    }

    /**
     * An authenticator that authenticates based on extracting Token from request
     */
    interface TokenAuthenticator extends Authenticator {

        /**
         * In case we need to extract token from header
         * the key must have this prefix
         */
        String HEADER_KEY = "header:" ;

        /**
         * In case we need to extract token from body
         * the key must have this prefix
         */
        String BODY_KEY = "body:" ;

        /**
         * To extract token from Request
         * the string is used as the key expression
         * "header:foo" or "body:token"
         * @return expression which would be used to gather the token from the Request
         */
        String tokenExpression();

        /**
         * Users XPath expression to extract token out from body
         * Simple header name for header
         * @param request a spark.Request to gather token from
         * @return token string if possible
         * @throws Exception in case of error , IllegalArgumentException in case protocol does not match
         */
        default String token(Request request) throws Exception {
            final String expr = tokenExpression();
            if ( expr.startsWith(HEADER_KEY) ){
                String key = expr.replace(HEADER_KEY,"");
                return request.headers(key);
            }
            if ( expr.startsWith(BODY_KEY) ){
                String key = expr.replace(BODY_KEY,"");
                Object body = ZTypes.json( request.body() );
                return ZMethodInterceptor.Default.jxPath(body, key, "").toString();
            }
            throw new IllegalArgumentException(expr);
        }

        /**
         * Given a token tries to find User from it
         * @param token input token string
         * @return an UserInfo Object
         * @throws Exception in case of any error
         */
        UserInfo userFromToken(String token) throws Exception ;
        @Override
        default UserInfo userInfo(Request request) throws Exception {
            String token = Authenticator.safeAuthExecute( () -> token(request));
            return Authenticator.safeAuthExecute(() -> userFromToken(token));
        }

        /**
         * A LRU Cache based Authenticator
         */
        abstract class CachedAuthenticator implements TokenAuthenticator {
            /**
             * default capacity
             */
            protected int maxCapacity = 1024;

            /**
             * Gets the size of the cache
             * @return size of the cache
             */
            public int cacheSize(){
                return maxCapacity;
            }

            /**
             * The underlying cache
             */
            protected final Map<String,UserInfo> lru = Collections.synchronizedMap(new LinkedHashMap<>(){
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > maxCapacity;
                }
            });

            /**
             * Tries to gather user information from token via various routes
             * @param token a string token
             * @return an UserInfo object
             * @throws Exception in case of any error
             */
            protected abstract UserInfo tryGetUserInfo(String token) throws Exception ;

            @Override
            public UserInfo userFromToken(String token) throws Exception {
                if ( lru.containsKey( token ) ){
                    return lru.get(token);
                }
                UserInfo userInfo = Authenticator.safeAuthExecute( () -> tryGetUserInfo(token));
                lru.put(userInfo.token(), userInfo);
                return userInfo;
            }
        }
    }
}