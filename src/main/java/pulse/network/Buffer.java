package pulse.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.Serial;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public final class Buffer implements AutoCloseable {
    public static final ByteOrder BYTE_ORDER = ByteOrder.nativeOrder();

    final Arena arena;
    final MemorySegment segment;

    long readIndex, writeIndex;

    public Buffer(long capacity) {
        final Arena arena = Arena.ofShared();
        this(arena, arena.allocate(capacity));
    }

    public Buffer(Arena arena, long capacity) {
        this(arena, arena.allocate(capacity));
    }

    public Buffer(Arena arena, MemorySegment segment) {
        this.arena = arena;
        this.segment = segment;
    }

    @Override
    public void close() {
        // no ownership?
        // arena.close();
    }

    public Buffer clear() {
        readIndex = 0;
        writeIndex = 0;
        return this;
    }

    public Buffer slice(long index, long length) {
        var slice = segment.asSlice(index, length);
        return new Buffer(arena, slice);
    }

    public void ensureWritable(long length) {
        if (writable() < length) {
            // TODO: resize strategies
            throw new OverflowException("Buffer is full and cannot be resized: " + capacity() + " -> " + (writeIndex + length));
        }
    }

    public long capacity() {
        return segment.byteSize();
    }

    public long readable() {
        return writeIndex - readIndex;
    }

    public long writable() {
        return capacity() - writeIndex;
    }

    public Arena getArena() {
        return arena;
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public long getReadIndex() {
        return readIndex;
    }

    public void setReadIndex(long readIndex) {
        this.readIndex = readIndex;
    }

    public long getWriteIndex() {
        return writeIndex;
    }

    public void setWriteIndex(long writeIndex) {
        this.writeIndex = writeIndex;
    }

    @FunctionalInterface
    public interface Reader<T> {
        T read(Buffer buffer);
    }

    @FunctionalInterface
    public interface Writer<T> {
        void write(Buffer buffer, T t);
    }

    @FunctionalInterface
    public interface Writable {
        Writable DUMMY = _ -> {
        };

        void writeSelfInto(Buffer buffer);
    }

    public void compactAfterRead() {
        long remaining = readable();
        MemorySegment.copy(segment, Buffer.BYTE_LAYOUT, readIndex, segment, Buffer.BYTE_LAYOUT, 0, remaining);
        readIndex = 0;
        writeIndex = remaining;
    }

    public int readFrom(ReadableByteChannel channel) throws IOException {
        var buffer = segment.asSlice(writeIndex, writable()).asByteBuffer().order(BYTE_ORDER);

        int count = channel.read(buffer);
        if (count == -1) throw new EOFException("Disconnected");
        writeIndex += count;

        return count;
    }

    public boolean writeInto(WritableByteChannel channel) throws IOException {
        if (readable() < 1) return true;

        var buffer = segment.asSlice(readIndex, readable()).asByteBuffer().order(BYTE_ORDER);

        int count = channel.write(buffer);
        if (count == -1) throw new EOFException("Disconnected");
        readIndex += count;

        return !buffer.hasRemaining();
    }

    public static final ValueLayout.OfByte BYTE_LAYOUT = ValueLayout.JAVA_BYTE;

    public byte readByte() {
        byte value = segment.get(BYTE_LAYOUT, readIndex);
        readIndex += 1;
        return value;
    }

    public void writeByte(byte value) {
        ensureWritable(1);
        segment.set(BYTE_LAYOUT, writeIndex, value);
        writeIndex += 1;
    }

    public byte[] readBytes(int count) {
        byte[] array = new byte[count];
        MemorySegment.copy(segment, BYTE_LAYOUT, readIndex, array, 0, count);
        readIndex += count;
        return array;
    }

    public void writeBytes(byte[] value) {
        ensureWritable(value.length);
        MemorySegment.copy(value, 0, segment, BYTE_LAYOUT, writeIndex, value.length);
        writeIndex += value.length;
    }

    public static final ValueLayout.OfBoolean BOOLEAN_LAYOUT = ValueLayout.JAVA_BOOLEAN.withOrder(BYTE_ORDER);

    public boolean readBoolean() {
        boolean value = segment.get(BOOLEAN_LAYOUT, readIndex);
        readIndex += 1;
        return value;
    }

    public void writeBoolean(boolean value) {
        ensureWritable(1);
        segment.set(BOOLEAN_LAYOUT, writeIndex, value);
        writeIndex += 1;
    }

    public static final ValueLayout.OfChar CHAR_LAYOUT = ValueLayout.JAVA_CHAR.withOrder(BYTE_ORDER).withByteAlignment(1);

    public char readChar() {
        char value = segment.get(CHAR_LAYOUT, readIndex);
        readIndex += 2;
        return value;
    }

    public void writeChar(char value) {
        ensureWritable(2);
        segment.set(CHAR_LAYOUT, writeIndex, value);
        writeIndex += 2;
    }

    public static final ValueLayout.OfShort SHORT_LAYOUT = ValueLayout.JAVA_SHORT.withOrder(BYTE_ORDER).withByteAlignment(1);

    public short readShort() {
        short value = segment.get(SHORT_LAYOUT, readIndex);
        readIndex += 2;
        return value;
    }

    public void writeShort(short value) {
        ensureWritable(2);
        segment.set(SHORT_LAYOUT, writeIndex, value);
        writeIndex += 2;
    }

    public short getShort(long index) {
        return segment.get(SHORT_LAYOUT, index);
    }

    public void setShort(long index, short value) {
        segment.set(Buffer.SHORT_LAYOUT, index, value);
    }

    public static final ValueLayout.OfInt INT_LAYOUT = ValueLayout.JAVA_INT.withOrder(BYTE_ORDER).withByteAlignment(1);

    public int readInt() {
        int value = segment.get(INT_LAYOUT, readIndex);
        readIndex += 4;
        return value;
    }

    public void writeInt(int value) {
        ensureWritable(4);
        segment.set(INT_LAYOUT, writeIndex, value);
        writeIndex += 4;
    }

    public static final ValueLayout.OfLong LONG_LAYOUT = ValueLayout.JAVA_LONG.withOrder(BYTE_ORDER).withByteAlignment(1);

    public long readLong() {
        long value = segment.get(LONG_LAYOUT, readIndex);
        readIndex += 8;
        return value;
    }

    public void writeLong(long value) {
        ensureWritable(8);
        segment.set(LONG_LAYOUT, writeIndex, value);
        writeIndex += 8;
    }

    public static final ValueLayout.OfFloat FLOAT_LAYOUT = ValueLayout.JAVA_FLOAT.withOrder(BYTE_ORDER).withByteAlignment(1);

    public float readFloat() {
        float value = segment.get(FLOAT_LAYOUT, readIndex);
        readIndex += 4;
        return value;
    }

    public void writeFloat(float value) {
        ensureWritable(4);
        segment.set(FLOAT_LAYOUT, writeIndex, value);
        writeIndex += 4;
    }

    public static final ValueLayout.OfDouble DOUBLE_LAYOUT = ValueLayout.JAVA_DOUBLE.withOrder(BYTE_ORDER).withByteAlignment(1);

    public double readDouble() {
        double value = segment.get(DOUBLE_LAYOUT, readIndex);
        readIndex += 8;
        return value;
    }

    public void writeDouble(double value) {
        ensureWritable(8);
        segment.set(DOUBLE_LAYOUT, writeIndex, value);
        writeIndex += 8;
    }

    public <T> T read(Reader<T> reader) {
        return reader.read(this);
    }

    public <T> void write(Writer<T> writer, T value) {
        writer.write(this, value);
    }

    public int readVarInt() {
        int k = readByte();
        if ((k & 0x80) != 128) {
            return k;
        }

        int i = k & 0x7F;
        for (int j = 1; j < 4; j++) {
            k = readByte();
            i |= (k & 0x7F) << j * 7;
            if ((k & 0x80) != 128) {
                return i;
            }
        }

        throw new IllegalStateException("Bad VarInt");
    }

    public void writeVarInt(int value) {
        // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            writeByte((byte) value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            writeShort((short) w);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            ensureWritable(3);
            int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
            segment.set(BYTE_LAYOUT, writeIndex, (byte) (w >>> 16));
            segment.set(BYTE_LAYOUT, writeIndex + 1, (byte) (w >>> 8));
            segment.set(BYTE_LAYOUT, writeIndex + 2, (byte) w);
            writeIndex += 3;
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16) | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
            writeInt(w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16 | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
            writeInt(w);
            writeByte((byte) (value >>> 28));
        }
    }

    public void setVarInt(long index, int value) {
        // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            segment.set(BYTE_LAYOUT, index, (byte) value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            segment.set(SHORT_LAYOUT, index, (short) w);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
            segment.set(BYTE_LAYOUT, index, (byte) (w >>> 16));
            segment.set(BYTE_LAYOUT, index + 1, (byte) (w >>> 8));
            segment.set(BYTE_LAYOUT, index + 2, (byte) w);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16) | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
            segment.set(INT_LAYOUT, index, w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16 | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
            segment.set(INT_LAYOUT, index, w);
            segment.set(BYTE_LAYOUT, index + 4, (byte) (value >>> 28));
        }
    }

    public void writeUtf8(String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        ensureWritable(bytes.length);
        writeBytes(bytes);
    }

    public String readUtf8() {
        int length = readVarInt();
        byte[] bytes = readBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        int length = (int) segment.byteSize();
        byte[] data = new byte[length];
        segment.asByteBuffer().get(data);

        StringBuilder sb = new StringBuilder();
        sb.append("readIndex: ").append(readIndex).append(" writeIndex: ").append(writeIndex)
                .append(System.lineSeparator());

        sb.append("[");
        for (int i = 0; i < data.length; i++) {
            sb.append(Byte.toUnsignedInt(data[i]));
            if (i < data.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");

        return sb.toString();
    }

    public static final class OverflowException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 901754468941968113L;

        OverflowException(String message) {
            super(message);
        }
    }
}
