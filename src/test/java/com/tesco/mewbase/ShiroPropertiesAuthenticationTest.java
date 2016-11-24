package com.tesco.mewbase;

import com.tesco.mewbase.auth.AuthenticationTestBase;
import com.tesco.mewbase.auth.MewbaseVertxAuthProvider;
import com.tesco.mewbase.bson.BsonObject;
import com.tesco.mewbase.client.Client;
import com.tesco.mewbase.client.ClientOptions;
import com.tesco.mewbase.log.impl.file.FileLogManagerOptions;
import com.tesco.mewbase.server.ServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.shiro.PropertiesProviderConstants;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthOptions;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.ExecutionException;

@RunWith(VertxUnitRunner.class)
public class ShiroPropertiesAuthenticationTest extends AuthenticationTestBase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    protected void setupAuthClient(BsonObject authInfo) {
        ClientOptions clientOptions = createClientOptions(authInfo);
        client = Client.newClient(vertx, clientOptions);
    }

    protected ServerOptions createServerOptions(File logDir) {
        FileLogManagerOptions fileLogManagerOptions = new FileLogManagerOptions().setLogDir(logDir.getPath());

        return new ServerOptions().setChannels(new String[]{TEST_CHANNEL_1, TEST_CHANNEL_2})
                .setFileLogManagerOptions(fileLogManagerOptions)
                .setAuthProvider(new MewbaseVertxAuthProvider(createShiroAuthProvider()));
    }

    private ShiroAuth createShiroAuthProvider() {
        JsonObject config = new JsonObject();
        config.put(PropertiesProviderConstants.PROPERTIES_PROPS_PATH_FIELD, "classpath:test-shiro-auth.properties");

        ShiroAuthOptions shiroAuthOptions = new ShiroAuthOptions().setType(ShiroAuthRealmType.PROPERTIES).setConfig(config);

        return ShiroAuth.create(vertx, shiroAuthOptions);
    }

    @Test
    public void testSuccessfulAuthentication(TestContext context) throws Exception {
        BsonObject authInfo = new BsonObject().put("username", "mew").put("password", "base");
        execSimplePubSub(context, authInfo);
    }

    @Test
    public void testFailedAuthentication(TestContext context) throws Exception {
        thrown.expect(ExecutionException.class);

        //TODO: When error messages and codes are centralized this should be changed
        thrown.expectMessage("org.apache.shiro.authc.UnknownAccountException");

        BsonObject authInfo = new BsonObject().put("username", "error").put("password", "error");
        execSimplePubSub(context, authInfo);
    }

}