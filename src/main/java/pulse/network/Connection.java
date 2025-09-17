package pulse.network;

import org.jctools.queues.MpscUnboundedXaddArrayQueue;
import org.jetbrains.annotations.Nullable;
import pulse.util.ObjectPool;
import pulse.util.WaitGroup;

import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class Connection implements AutoCloseable, Runnable {
    public static final int PACKET_HEADER_SIZE = 2;

    public static final int MAX_PACKET_SIZE = 1024;
    public static final int MAX_PACKET_PAYLOAD_SIZE = MAX_PACKET_SIZE - PACKET_HEADER_SIZE;

    public static short readHeader(Buffer buffer) {
        return buffer.readShort();
    }

    public static void setHeader(Buffer buffer, long index, short length) {
        buffer.setShort(index, length);
    }

    public static final Arena MEM_ARENA = Arena.ofShared();
    public static final ObjectPool<Buffer> BUFFER_POOL = ObjectPool.pool(() -> new Buffer(MEM_ARENA, MAX_PACKET_SIZE), Buffer::clear);

    private static final int WRITE_QUEUE_CHUNK_SIZE = 64;

    private final MpscUnboundedXaddArrayQueue<Buffer.Writable> packetQueue = new MpscUnboundedXaddArrayQueue<>(WRITE_QUEUE_CHUNK_SIZE);
    private final WaitGroup waitGroup = new WaitGroup();

    private final SocketChannel channel;
    private final SocketAddress remoteAddress;
    private final Processor processor;

    private final Thread readThread, writeThread;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Condition writeCondition = writeLock.newCondition();

    private volatile boolean active;

    private @Nullable Buffer writeLeftover = null;

    public Connection(SocketChannel channel, Processor processor) throws IOException {
        this.configureSocket(channel);

        this.channel = channel;
        this.remoteAddress = channel.getRemoteAddress();
        this.processor = processor;

        this.readThread = this.unstartedVirtualThread(this::readLoop, "pulse-network-read");
        this.writeThread = this.unstartedVirtualThread(this::writeLoop, "pulse-network-write");
    }

    @Override
    public void run() {
        // throws exception if the thread was already started
        readThread.start();
        writeThread.start();

        active = true;
        waitGroup.add(2);
    }

    void read(Buffer buffer) throws IOException {
        int _ = buffer.readFrom(channel);

        while (buffer.readable() > 0) {
            var anchor = buffer.readIndex;
            var length = readHeader(buffer);

            if (length < 0)
                throw new IllegalArgumentException("length < 0");
            if (length > MAX_PACKET_PAYLOAD_SIZE)
                throw new IllegalArgumentException("too large packet");

            if (length > buffer.readable()) {
                buffer.readIndex = anchor;
                break; // not enough data
            }

            var payload = buffer.slice(buffer.readIndex, length);
            buffer.readIndex += length;

            payload.writeIndex += length;
            processor.process(this, payload);
        }

        buffer.compactAfterRead();
    }

    void readLoop() {
        try (var holder = BUFFER_POOL.hold()) {
            var buffer = holder.get();
            while (active) {
                try {
                    read(buffer);
                } catch (ClosedChannelException ignored) {
                    break; // we closed socket during read
                } catch (EOFException e) {
                    close();
                    break;
                } catch (Throwable e) {
                    boolean isExpected = e instanceof SocketException && e.getMessage().equals("Connection reset");
                    if (!isExpected) {
                        //noinspection CallToPrintStackTrace
                        e.printStackTrace();
                    }
                    close();
                    break;
                }
            }
        }
    }

    void write() throws IOException {
        writeLock.lock();
        try {
            // spurious wakeups?
            writeCondition.await();
            writeSync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("#write interrupted!", e);
        } finally {
            writeLock.unlock();
        }
    }

    void writeSync() throws IOException {
        var leftover = this.writeLeftover;
        if (leftover != null) {
            if (leftover.writeInto(channel)) {
                this.writeLeftover = null;
                BUFFER_POOL.add(leftover);
            } else {
                // failed to write the whole leftover, try again next flush
                return;
            }
        }

        assert this.writeLeftover == null;

        var queue = packetQueue;
        if (queue.isEmpty()) return;

        long startIndex;
        Buffer.Writable packet;

        var buffer = BUFFER_POOL.get();
        try {
            while ((packet = queue.peek()) != null) {
                startIndex = buffer.writeIndex;
                buffer.writeIndex += PACKET_HEADER_SIZE;

                try {
                    packet.writeSelfInto(buffer);
                    setHeader(buffer, startIndex, (short) (buffer.writeIndex - (startIndex + PACKET_HEADER_SIZE)));

                    queue.poll();
                } catch (Buffer.OverflowException e) {
                    buffer.writeIndex = startIndex;

                    if (startIndex == 0) {
                        // queue.poll();
                        throw new IllegalStateException("Packet is too large: " + packet);
                    }

                    if (buffer.writeInto(channel)) {
                        buffer.clear();
                    } else {
                        this.writeLeftover = buffer;
                        // failed to write the whole buffer, try again next flush
                        return;
                    }
                }
            }

            if (buffer.readable() > 0 && !buffer.writeInto(channel)) {
                // failed to write the whole buffer, try again next flush
                this.writeLeftover = buffer;
            } else {
                BUFFER_POOL.add(buffer);
            }
        } catch (Throwable t) {
            BUFFER_POOL.add(buffer);
            throw t;
        }
    }

    void writeLoop() {
        while (active) {
            try {
                write();
            } catch (ClosedChannelException ignored) {
                break; // we closed socket during write
            } catch (EOFException ignored) {
                break;
            } catch (Throwable e) {
                boolean isExpected = e instanceof IOException && e.getMessage().equals("Broken pipe");
                if (!isExpected) {
                    //noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }
                break;
            }
        }

        try {
            channel.close();
        } catch (IOException ignored) {
            // disconnect
        }

        if (writeLeftover != null) {
            BUFFER_POOL.add(writeLeftover);
            writeLeftover = null;
        }

        // System.out.println("disconnected");
    }

    @Override
    public void close() {
        active = false;
        flush();
    }

    public void flush() {
        writeLock.lock();
        try {
            writeCondition.signal();
        } finally {
            writeLock.unlock();
        }
    }

    public boolean write(Buffer.Writable writable) {
        return packetQueue.relaxedOffer(writable);
    }

    public boolean writeAndFlush(Buffer.Writable writable) {
        var result = write(writable);
        flush();
        return result;
    }

    public void await() {
        waitGroup.await();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isActive() {
        return active;
    }

    @FunctionalInterface
    public interface Processor {
        void process(Connection connection, Buffer buffer);
    }

    private void configureSocket(SocketChannel channel) throws IOException {
        if (channel.getLocalAddress() instanceof InetSocketAddress) {
            final Socket socket = channel.socket();
            socket.setSendBufferSize(8192);
            socket.setReceiveBufferSize(4096);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(15_000);
        }
    }

    private Thread unstartedVirtualThread(Runnable runnable, String name) {
        return Thread.ofVirtual().name(name).unstarted(() -> {
            try {
                runnable.run();
            } finally {
                waitGroup.done();
            }
        });
    }
}
