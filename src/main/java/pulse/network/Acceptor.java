package pulse.network;

import pulse.util.WaitGroup;

import java.io.IOException;
import java.net.*;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;

public final class Acceptor implements AutoCloseable, Runnable {
    private final WaitGroup waitGroup;
    private final Connection.Processor processor;

    private final ServerSocketChannel serverSocket;
    private final SocketAddress socketAddress;
    private final String address;
    private int port;

    private volatile boolean active;

    public Acceptor(SocketAddress socketAddress, WaitGroup waitGroup, Connection.Processor processor) throws IOException {
        final ProtocolFamily family = switch (socketAddress) {
            case InetSocketAddress inetSocketAddress -> {
                this.address = inetSocketAddress.getHostString();
                this.port = inetSocketAddress.getPort();
                yield inetSocketAddress.getAddress().getAddress().length == 4 ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6;
            }
            case UnixDomainSocketAddress unixDomainSocketAddress -> {
                this.address = "unix://" + unixDomainSocketAddress.getPath();
                this.port = 0;
                yield StandardProtocolFamily.UNIX;
            }
            default ->
                    throw new IllegalArgumentException("address must be an InetSocketAddress or a UnixDomainSocketAddress");
        };

        ServerSocketChannel server = ServerSocketChannel.open(family);
        server.bind(socketAddress);
        this.serverSocket = server;
        this.socketAddress = socketAddress;

        if (socketAddress instanceof InetSocketAddress && port == 0) {
            port = server.socket().getLocalPort();
        }

        this.waitGroup = waitGroup;
        this.processor = processor;
    }

    @Override
    public void run() {
        active = true;
        this.startVirtualThread(this::acceptLoop, "pulse-network-acceptor");
    }

    void accept() throws IOException {
        final SocketChannel channel = serverSocket.accept();

        final Connection connection = new Connection(channel, processor);
        connection.run();
    }

    void acceptLoop() {
        while (active) {
            try {
                this.accept();
            } catch (AsynchronousCloseException ignored) {
                // we are exiting
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() {
        active = false;

        try {
            serverSocket.close();

            if (socketAddress instanceof UnixDomainSocketAddress unixDomainSocketAddress) {
                Files.deleteIfExists(unixDomainSocketAddress.getPath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void await() {
        waitGroup.await();
    }

    public SocketAddress socketAddress() {
        return socketAddress;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public boolean isActive() {
        return active;
    }

    private void startVirtualThread(Runnable runnable, String name) {
        waitGroup.add(1);
        Thread.ofVirtual().name(name).start(() -> {
            try {
                runnable.run();
            } finally {
                waitGroup.done();
            }
        });
    }
}
