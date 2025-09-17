package pulse;

import org.jetbrains.annotations.Nullable;
import pulse.network.Buffer;
import pulse.network.Connection;
import pulse.network.Type;
import pulse.util.DefiningClassLoader;
import space.vectrix.flare.fastutil.Int2ObjectSyncMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class PulseClient implements AutoCloseable {
    private final DefiningClassLoader classLoader = new DefiningClassLoader();
    private final Map<Class<?>, Type<?>> typeMap = new IdentityHashMap<>();

    private final AtomicInteger callbackId = new AtomicInteger();
    private final Int2ObjectSyncMap<@Nullable CompletableFuture<Buffer>> callbackMap = Int2ObjectSyncMap.hashmap();

    private final Connection.Processor connectionProcessor;
    private final AtomicReference<@Nullable Connection> connectionRef = new AtomicReference<>(null);

    public PulseClient() {
        // cannot reference 'this' before superclass constructor is called
        this(null);
    }

    public PulseClient(@Nullable Connection.Processor processor) {
        this.connectionProcessor = processor == null ? this::process : processor;
    }

    public boolean connect(int port) throws IOException {
        return this.connect(new InetSocketAddress(port));
    }

    // TODO: wait until connected
    public boolean connect(SocketAddress target) throws IOException {
        final SocketChannel channel = SocketChannel.open();
        channel.connect(target);

        final Connection connection = new Connection(channel, connectionProcessor);

        if (connectionRef.compareAndSet(null, connection)) {
            connection.run();
            return true;
        } else {
            channel.close();
            return false;
        }
    }

    @Override
    public void close() {
        final Connection connection = connectionRef.getAndSet(null);
        if (connection != null) connection.close();

        // TODO: cancel callbacks
    }

    public void await() {
        final Connection connection = this.connectionRef.get();
        if (connection != null) connection.await();
    }

    public boolean write(Buffer.Writable writable) {
        final Connection connection = this.connectionRef.get();
        return connection != null && connection.write(writable);
    }

    public boolean writeAndFlush(Buffer.Writable writable) {
        final Connection connection = this.connectionRef.get();
        return connection != null && connection.writeAndFlush(writable);
    }

    public void flush() {
        final Connection connection = this.connectionRef.get();
        if (connection != null) connection.flush();
    }

    public Future<Buffer> call(Buffer.Writable payload) {
        var callbackId = this.callbackId.getAndIncrement();

        Buffer.Writable packet = buffer -> {
            buffer.writeInt(callbackId);
            payload.writeSelfInto(buffer);
        };

        CompletableFuture<Buffer> future = new CompletableFuture<>();
        callbackMap.put(callbackId, future);

        if (!writeAndFlush(packet)) {
            callbackMap.remove(callbackId);
            future.completeExceptionally(new IllegalStateException("No active connection/Failed to queue the packet: " + payload));
            return future;
        }

        // TODO: configurable timeout
        return future;
    }

    public void process(Connection connection, Buffer buffer) {
        int callbackId = buffer.readInt();

        var future = callbackMap.remove(callbackId);
        if (future == null) return;

        // is successful?
        if (buffer.readBoolean()) {
            var length = buffer.readable();
            var slice = buffer.slice(buffer.getReadIndex(), length);
            slice.setWriteIndex(length);

            future.complete(slice);
        } else {
            var message = buffer.readUtf8();
            var exception = new RemoteRuntimeException(message);

            future.completeExceptionally(exception);
        }
    }

    // id param should be removed
    public <T> T use(int id, Class<T> type) {
        if (!type.isInterface())
            throw new IllegalArgumentException("Must be an interface: " + type);
        try {
            var bytes = CodeGen.implementClient(type, id);
            var generatedTypeName = type.getName() + '$' + CodeGen.GENERATED_CLIENT_SUFFIX;
            var newType = classLoader.define(generatedTypeName, bytes, 0, bytes.length);
            //noinspection unchecked
            return (T) newType.getConstructors()[0].newInstance(this, typeMap);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void registerType(Type<T> type) {
        typeMap.put(type.asClass(), type);
    }

    public <T> void registerType(Class<T> clazz, Type<T> type) {
        typeMap.put(clazz, type);
    }

    public <T> @Nullable Type<T> getType(Class<T> type) {
        //noinspection unchecked
        return (Type<T>) typeMap.get(type);
    }

    public DefiningClassLoader getClassLoader() {
        return classLoader;
    }
}
