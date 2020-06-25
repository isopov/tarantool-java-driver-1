package io.tarantool.driver;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.tarantool.driver.core.TarantoolChannelInitializer;
import io.tarantool.driver.exceptions.TarantoolSpaceNotFoundException;
import io.tarantool.driver.mappers.MessagePackObjectMapper;
import io.tarantool.driver.mappers.MessagePackValueMapper;
import io.tarantool.driver.metadata.TarantoolMetadata;
import io.tarantool.driver.metadata.TarantoolMetadataOperations;
import io.tarantool.driver.auth.SimpleTarantoolCredentials;
import io.tarantool.driver.auth.TarantoolCredentials;
import io.tarantool.driver.core.RequestManager;
import io.tarantool.driver.core.RequestFutureManager;
import io.tarantool.driver.metadata.TarantoolSpaceMetadata;
import io.tarantool.driver.space.TarantoolSpace;
import io.tarantool.driver.space.TarantoolSpaceOperations;
import io.tarantool.driver.util.Assert;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main class for connecting to a single Tarantool server. Provides basic API for interacting with the database
 * and manages connections.
 *
 * @author Alexey Kuzin
 */
public class StandaloneTarantoolClient implements TarantoolClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3301;
    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASSWORD = "password";
    private static final int DEFAULT_CONNECT_TIMEOUT = 1000; // milliseconds
    private static final int DEFAULT_READ_TIMEOUT = 1000; // milliseconds
    private static final int DEFAULT_REQUEST_TIMEOUT = 2000; // milliseconds

    private EventLoopGroup eventLoopGroup;
    private List<ChannelFuture> channelFutures;
    private TarantoolVersionHolder versionHolder;
    private RequestFutureManager requestFutureManager;
    private TarantoolClientConfig config;
    private Bootstrap bootstrap;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private RequestManager requestManager;

    /**
     * Create a client. Default credentials will be used.
     * @see InetSocketAddress
     */
    public StandaloneTarantoolClient() {
        this(new SimpleTarantoolCredentials(DEFAULT_USER, DEFAULT_PASSWORD));
    }

    /**
     * Create a client using provided credentials information.
     * @param credentials Tarantool user credentials holder
     * @see TarantoolCredentials
     */
    public StandaloneTarantoolClient(TarantoolCredentials credentials) {
       this(new TarantoolClientConfig.Builder()
               .withCredentials(credentials)
               .withReadTimeout(DEFAULT_READ_TIMEOUT)
               .withConnectTimeout(DEFAULT_CONNECT_TIMEOUT)
               .withRequestTimeout(DEFAULT_REQUEST_TIMEOUT)
               .build());
    }

    /**
     * Create a client.
     * @param config the client configuration
     * @see TarantoolClientConfig
     */
    public StandaloneTarantoolClient(TarantoolClientConfig config) {
        this.config = config;
        eventLoopGroup = new NioEventLoopGroup();
        channelFutures = new LinkedList<>();
        versionHolder = new TarantoolVersionHolder();
        requestFutureManager = new RequestFutureManager(config);
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                .handler(new TarantoolChannelInitializer(config, versionHolder, requestFutureManager)); // TODO +pool
    }

    /**
     * Connect to a Tarantool server on localhost using the default port (3301)
     * @return connected client
     * @throws TarantoolClientException when connection or request for metadata are failed
     */
    public StandaloneTarantoolClient connect() throws TarantoolClientException {
        return connect(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * Connect to a Tarantool server using the specified host and the default port (3301).
     * @param host valid host name or IP address
     * @return connected client
     * @see InetSocketAddress
     * @throws TarantoolClientException when connection or request for metadata are failed
     */
    public StandaloneTarantoolClient connect(String host) throws TarantoolClientException {
        return connect(host, DEFAULT_PORT);
    }

    /**
     * Connect to a Tarantool server using the specified host and port.
     * @param host valid host name or IP address
     * @param port valid port
     * @return connected client
     * @see InetSocketAddress
     * @throws TarantoolClientException when connection or request for metadata are failed
     */
    public StandaloneTarantoolClient connect(String host, int port) throws TarantoolClientException {
        return connect(new InetSocketAddress(host, port));
    }

    @Override
    public StandaloneTarantoolClient connect(InetSocketAddress address) throws TarantoolClientException {
        ChannelFuture future = bootstrap.clone().remoteAddress(address).connect();
        channelFutures.add(future);
        AtomicReference<TarantoolClientException> caughtException = new AtomicReference<>();
        future.addListener(channelFuture -> {
            if (channelFuture.isSuccess()) {
                connected.set(true);
                try {
                    this.metadata().refresh();
                } catch (TarantoolClientException e) {
                    caughtException.set(e);
                }
            }
        });
        future.awaitUninterruptibly();
        if (caughtException.get() != null) {
            throw caughtException.get();
        }
        this.requestManager = new RequestManager(future.channel(), requestFutureManager);
        return this;
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public TarantoolVersion getVersion() throws TarantoolClientException {
        if (!isConnected()) {
            throw new TarantoolClientException("The client is not connected to Tarantool server");
        }
        return versionHolder.getVersion();
    }

    @Override
    public TarantoolSpaceOperations space(String spaceName) throws TarantoolClientException {
        Assert.hasText(spaceName, "Space name must not be null or empty");

        if (!isConnected()) {
            throw new TarantoolClientException("The client is not connected to Tarantool server");
        }
        Optional<TarantoolSpaceMetadata> meta = this.metadata().getSpaceByName(spaceName);
        if (!meta.isPresent()) {
            throw new TarantoolSpaceNotFoundException(spaceName);
        }
        return new TarantoolSpace(meta.get().getSpaceId(), this, requestManager);
    }

    @Override
    public TarantoolSpaceOperations space(int spaceId) throws TarantoolClientException {
        Assert.state(spaceId > 0, "Space ID must be greater than 0");

        if (!isConnected()) {
            throw new TarantoolClientException("The client is not connected to Tarantool server");
        }
        return new TarantoolSpace(spaceId, this, requestManager);
    }

    @Override
    public TarantoolMetadataOperations metadata() throws TarantoolClientException {
        if (!isConnected()) {
            throw new TarantoolClientException("The client is not connected to Tarantool server");
        }
        return new TarantoolMetadata(this);
    }

    @Override
    public <T> CompletableFuture<T> call(String functionName, Object... arguments) throws TarantoolClientException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public <T> CompletableFuture<T> call(String functionName, List<Object> arguments,
                                         MessagePackValueMapper resultMapper) throws TarantoolClientException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public <T> CompletableFuture<T> call(String functionName, List<Object> arguments,
                                         MessagePackObjectMapper argumentsMapper,
                                         MessagePackValueMapper resultMapper) throws TarantoolClientException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public TarantoolClientConfig getConfig() {
        return config;
    }

    @Override
    public void close() throws IOException {
        try {
            for (ChannelFuture f: channelFutures) {
                connected.compareAndSet(true, false);
                f.channel().close();
                f.channel().closeFuture().sync();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                eventLoopGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
