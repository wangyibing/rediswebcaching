package org.zouzias.rediscachingfilter.tests;

import org.zouzias.rediscachingfilter.redis.RedisConnector;
import java.io.IOException;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 *
 * @author zouzias
 */
public class CachedServletTest {

    private static final Logger logger = Logger.getLogger(CachedServletTest.class);

    private Server server;
    private String JETTY_URL;
    private final int port = 8083;

    @Before
    public void setUp() throws IOException, SAXException, Exception {
        // Create a basic jetty server object that will listen on port 8080.  Note that if you set this to port 0
        // then a randomly available port will be assigned that you can either look in the logs for the port,
        // or programmatically obtain it for use in test cases.
        server = new Server(port);

        JETTY_URL = "http://localhost:" + port;
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setWar("target/RedisCachingFilter-1.0.war");
        server.setHandler(webapp);

        server.start();
    }

    public CachedServletTest() {
    }

    @Test
    public void testCachedGETMethodServlet() throws IOException {

        String url = JETTY_URL + "/cached/CachedServlet";
        String content = null;

        // Create an instance of HttpClient.
        HttpClient client = new HttpClient();
        // Create a method instance.
        GetMethod method = new GetMethod(JETTY_URL + "/cached/CachedServlet");

        // Provide custom retry handler is necessary
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new DefaultHttpMethodRetryHandler(3, false));

        try {
            // Execute the method.
            int statusCode = client.executeMethod(method);

            assertNotSame("Method failed: " + method.getStatusLine(), statusCode, HttpStatus.SC_OK);
            assertNotSame("Content Char set is not UTF-8.", "UTF-8", method.getResponseCharSet());

            // If redis server is running, check if the key is inserted.
            if (RedisConnector.getRedisConnection().getResource() != null){
                // Get the value from redis.
                String value = RedisConnector.getKey(url);
                assertNotNull("The key " + value + " is NOT in redis cache. It should be in cache.", value);
            
                // Delete the key from redis.
                RedisConnector.delKey(url);
            }
            
            // Read the response body.
            byte[] responseBody = method.getResponseBody();

            // Deal with the response.
            // Use caution: ensure correct character encoding and is not binary data
            content = new String(responseBody);

        } catch (HttpException e) {
            logger.error("Fatal protocol violation: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("Fatal transport error: " + e.getMessage(), e);
        } finally {
            // Release the connection.
            method.releaseConnection();
        }

        assertNotNull("No response received", content);
    }
        
    @Test
    public void testCachedPOSTMethodServlet() throws IOException {

        String URL = JETTY_URL + "/cached/CachedServlet";
        String postContent = "Test input string:timestamp:" + String.valueOf(System.currentTimeMillis());
        String content = null;

        // Create an instance of HttpClient.
        HttpClient client = new HttpClient();
        // Create a method instance.
        PostMethod method = new PostMethod(URL);

        // Add text POST content
        method.setRequestEntity(new StringRequestEntity(postContent));
        
        // Provide custom retry handler is necessary
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new DefaultHttpMethodRetryHandler(3, false));

        try {
            // Execute the method.
            int statusCode = client.executeMethod(method);

            assertNotSame("Method failed: " + method.getStatusLine(), statusCode, HttpStatus.SC_OK);
            assertNotSame("Content Char set is not UTF-8.", "UTF-8", method.getResponseCharSet());

            // If redis server is running, check if the key is inserted.
            if (RedisConnector.getRedisConnection().getResource() != null){
                 
                String hashedKey = RedisFilterCommons.stringifyHttpPostRequest(URL, postContent);
                
                logger.info("Hashed POST key is " + hashedKey);
                
                // Get the value from redis.
                String value = RedisConnector.getKey(hashedKey);
                assertNotNull("The key " + value + " is NOT in redis cache. It should be in cache.", value);
            
                // Delete the key from redis.
                RedisConnector.delKey(URL);
            }
            
            // Read the response body.
            byte[] responseBody = method.getResponseBody();

            // Deal with the response.
            // Use caution: ensure correct character encoding and is not binary data
            content = new String(responseBody);

        } catch (HttpException e) {
            logger.error("Fatal protocol violation: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("Fatal transport error: " + e.getMessage(), e);
        } finally {
            // Release the connection.
            method.releaseConnection();
        }

        assertNotNull("No response received", content);
    }

    @After
    public void tearDown() {
        try {
            server.stop();
            server.destroy();
        } catch (Exception ex) {
            logger.error("Jetty server did not stopp successfully...", ex);
        }
    }
}
