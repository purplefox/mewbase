package com.tesco.mewbase.server.impl;

import com.tesco.mewbase.bson.BsonObject;
import com.tesco.mewbase.common.Delivery;
import com.tesco.mewbase.doc.DocManager;
import com.tesco.mewbase.doc.impl.lmdb.LmdbDocManager;
import com.tesco.mewbase.function.FunctionManager;
import com.tesco.mewbase.function.impl.FunctionManagerImpl;
import com.tesco.mewbase.log.Log;
import com.tesco.mewbase.log.LogManager;
import com.tesco.mewbase.log.impl.file.FileAccess;
import com.tesco.mewbase.log.impl.file.FileLogManager;
import com.tesco.mewbase.log.impl.file.FileLogManagerOptions;
import com.tesco.mewbase.log.impl.file.faf.AFFileAccess;
import com.tesco.mewbase.server.Server;
import com.tesco.mewbase.server.ServerOptions;
import com.tesco.mewbase.server.impl.transport.net.NetTransport;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by tim on 22/09/16.
 */
public class ServerImpl implements Server {

    private final static Logger log = LoggerFactory.getLogger(ServerImpl.class);

    private final ServerOptions serverOptions;
    private final Vertx vertx;
    private final Set<ConnectionImpl> connections = new ConcurrentHashSet<>();
    private final LogManager logManager;
    private final DocManager docManager;
    private final FunctionManager functionManager;
    private final Set<Transport> transports = new ConcurrentHashSet<>();

    protected ServerImpl(Vertx vertx, ServerOptions serverOptions) {
        this.vertx = vertx;
        this.serverOptions = serverOptions;
        FileAccess faf = new AFFileAccess(vertx);
        FileLogManagerOptions sOptions = serverOptions.getFileLogManagerOptions();
        FileLogManagerOptions options = sOptions == null ? new FileLogManagerOptions() : sOptions;
        this.logManager = new FileLogManager(vertx, options, faf);
        this.docManager = new LmdbDocManager(serverOptions.getDocsDir(), vertx);
        this.functionManager = new FunctionManagerImpl(docManager, logManager);
    }

    protected ServerImpl(ServerOptions serverOptions) {
        this(Vertx.vertx(), serverOptions);
    }

    @Override
    public synchronized CompletableFuture<Void> start() {
        String[] channels = serverOptions.getChannels();
        String[] binders = serverOptions.getBinders();
        CompletableFuture[] all = new CompletableFuture[1 + (channels != null ? channels.length : 0) + 1 +
                (binders != null ? binders.length : 0)];
        int i = 0;
        // Start the channels
        if (serverOptions.getChannels() != null) {
            for (String channel : serverOptions.getChannels()) {
                all[i++] = logManager.createLog(channel);
            }
        }
        all[i++] = docManager.start();
        // Start the binders
        if (serverOptions.getBinders() != null) {
            for (String binder : serverOptions.getBinders()) {
                all[i++] = docManager.createBinder(binder);
            }
        }
        all[i] = startTransports();
        return CompletableFuture.allOf(all);
    }

    @Override
    public synchronized CompletableFuture<Void> stop() {
        CompletableFuture[] all = new CompletableFuture[1 + 1 + 1];
        int i = 0;
        all[i++] = stopTransports();
        all[i++] = docManager.close();
        all[i] = logManager.close();
        connections.clear();
        return CompletableFuture.allOf(all);
    }

    @Override
    public boolean installFunction(String name, String channel, Function<BsonObject, Boolean> eventFilter,
                                   String binderName, Function<BsonObject, String> docIDSelector,
                                   BiFunction<BsonObject, Delivery, BsonObject> function) {

        return functionManager.installFunction(name, channel, eventFilter, binderName, docIDSelector, function);
    }

    @Override
    public boolean deleteFunction(String functionName) {
        return functionManager.deleteFunction(functionName);
    }

    protected void removeConnection(ConnectionImpl connection) {
        connections.remove(connection);
    }

    protected Log getLog(String channel) {
        return logManager.getLog(channel);
    }

    private CompletableFuture<Void> startTransports() {
        // For now just net transport
        Transport transport = new NetTransport(vertx, serverOptions);
        transports.add(transport);
        transport.connectHandler(this::connectHandler);
        return transport.start();
    }

    private void connectHandler(TransportConnection transportConnection) {
        connections.add(new ConnectionImpl(this, transportConnection, Vertx.currentContext(), docManager));
    }

    private CompletableFuture<Void> stopTransports() {
        CompletableFuture[] all = new CompletableFuture[transports.size()];
        int i = 0;
        for (Transport transport: transports) {
            all[i++] = transport.stop();
        }
        transports.clear();
        return CompletableFuture.allOf(all);
    }

}
