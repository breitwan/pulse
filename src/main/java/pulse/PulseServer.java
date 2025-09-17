package pulse;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.Nullable;
import pulse.network.Acceptor;
import pulse.network.Buffer;
import pulse.network.Connection;
import pulse.network.Type;
import pulse.util.DefiningClassLoader;
import pulse.util.WaitGroup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class PulseServer implements AutoCloseable, Runnable {
    private final DefiningClassLoader classLoader = new DefiningClassLoader();
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    // TODO: handshake with checksum comparison? schemas?
    // private final Map<Class<?>, CodeGen.@Nullable Handle<?>> classToHandleMap = new IdentityHashMap<>();
    private final Int2ObjectMap<CodeGen.@Nullable Handle<?>> idToHandleMap = new Int2ObjectArrayMap<>();
    private final Map<Class<?>, Type<?>> typeMap = new IdentityHashMap<>();

    private final WaitGroup waitGroup = new WaitGroup();
    private final Acceptor acceptor;

    public PulseServer(int port) throws IOException {
        this(new InetSocketAddress(port));
    }

    public PulseServer(SocketAddress bind) throws IOException {
        this.acceptor = new Acceptor(bind, waitGroup, this::process);
    }

    @Override
    public void run() {
        acceptor.run();
    }

    @Override
    public void close() {
        acceptor.close();
    }

    public void await() {
        waitGroup.await();
    }

    public void process(Connection connection, Buffer buffer) {
        int callbackId = buffer.readInt();

        int serviceId = buffer.readVarInt();
        var handle = idToHandleMap.get(serviceId);
        if (handle == null) throw new IllegalStateException("No implementation found");

        // check is in generated code
        int methodId = buffer.readVarInt();

        var slice = buffer.slice(buffer.getReadIndex(), buffer.readable());
        handle.pulse$process(callbackId, methodId, slice, executor).whenComplete((payload, e) -> {
            Buffer.Writable response;

            if (e == null) {
                response = buf -> {
                    buf.writeInt(callbackId);
                    buf.writeBoolean(true);
                    payload.writeSelfInto(buf);
                };
            } else {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();

                response = buf -> {
                    buf.writeInt(callbackId);
                    buf.writeBoolean(false);
                    buf.writeUtf8(e.getMessage());
                };
            }

            connection.writeAndFlush(response);
        });
    }

    // id param should be replaced
    public <T> void export(int id, Class<T> type, Supplier<T> implementation) {
        if (!type.isInterface())
            throw new IllegalArgumentException("Must be an interface: " + type);
        try {
            var instance = implementation.get();
            var bytes = CodeGen.implementServer(type, instance);
            var generatedTypeName = type.getName() + '$' + CodeGen.GENERATED_SERVER_SUFFIX;
            var newType = classLoader.define(generatedTypeName, bytes, 0, bytes.length);
            //noinspection unchecked
            var handle = (CodeGen.Handle<T>) newType.getConstructors()[0].newInstance(this, instance, typeMap);
            idToHandleMap.put(id, handle);
            // classToHandleMap.put(type, handle);
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
