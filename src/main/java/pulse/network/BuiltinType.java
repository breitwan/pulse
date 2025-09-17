package pulse.network;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public sealed interface BuiltinType {
    static <E extends Enum<E>> EnumType<E> Enum(Class<E> type) {
        return new EnumType<>(type, type.getEnumConstants());
    }

    record EnumType<E extends Enum<E>>(Class<E> type, E[] values) implements BuiltinType, Type<E> {
        @Override
        public Class<E> asClass() {
            return type;
        }

        @Override
        public E read(Buffer buffer) {
            return values[buffer.readVarInt()];
        }

        @Override
        public void write(Buffer buffer, E value) {
            buffer.writeVarInt(value.ordinal());
        }
    }

    record TransformType<T, S>(Type<T> parent, Function<T, S> to, Function<S, T> from) implements BuiltinType, Type<S> {
        @Override
        public Class<S> asClass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public S read(Buffer buffer) {
            return to.apply(parent.read(buffer));
        }

        @Override
        public void write(Buffer buffer, S value) {
            parent.write(buffer, from.apply(value));
        }
    }

    record OptionalType<T>(Type<T> parent) implements BuiltinType, Type<T> {
        @Override
        public Class<T> asClass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable T read(Buffer buffer) {
            return buffer.readBoolean() ? buffer.read(parent) : null;
        }

        @Override
        public void write(Buffer buffer, @Nullable T t) {
            if (t == null) {
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(true);
                buffer.write(parent, t);
            }
        }
    }

    record StringUtf8Type() implements BuiltinType, Type<String> {
        @Override
        public Class<String> asClass() {
            return String.class;
        }

        @Override
        public String read(Buffer buffer) {
            return buffer.readUtf8();
        }

        @Override
        public void write(Buffer buffer, String string) {
            buffer.writeUtf8(string);
        }
    }

    record ByteType() implements BuiltinType, Type<Byte> {
        @Override
        public Class<Byte> asClass() {
            return Byte.class;
        }

        @Override
        public Byte read(Buffer buffer) {
            return buffer.readByte();
        }

        @Override
        public void write(Buffer buffer, Byte value) {
            buffer.writeByte(value);
        }
    }

    record ByteArrayType() implements BuiltinType, Type<byte[]> {
        @Override
        public Class<byte[]> asClass() {
            return byte[].class;
        }

        @Override
        public byte[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            return buffer.readBytes(length);
        }

        @Override
        public void write(Buffer buffer, byte[] array) {
            buffer.writeVarInt(array.length);
            buffer.writeBytes(array);
        }
    }

    record BooleanType() implements BuiltinType, Type<Boolean> {
        @Override
        public Class<Boolean> asClass() {
            return Boolean.class;
        }

        @Override
        public Boolean read(Buffer buffer) {
            return buffer.readBoolean();
        }

        @Override
        public void write(Buffer buffer, Boolean value) {
            buffer.writeBoolean(value);
        }
    }

    record BooleanArrayType() implements BuiltinType, Type<boolean[]> {
        @Override
        public Class<boolean[]> asClass() {
            return boolean[].class;
        }

        @Override
        public boolean[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            boolean[] array = new boolean[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readBoolean();
            return array;
        }

        @Override
        public void write(Buffer buffer, boolean[] array) {
            buffer.writeVarInt(array.length);
            for (boolean value : array) buffer.writeBoolean(value);
        }
    }

    record CharacterType() implements BuiltinType, Type<Character> {
        @Override
        public Class<Character> asClass() {
            return Character.class;
        }

        @Override
        public Character read(Buffer buffer) {
            return buffer.readChar();
        }

        @Override
        public void write(Buffer buffer, Character value) {
            buffer.writeChar(value);
        }
    }

    record CharArrayType() implements BuiltinType, Type<char[]> {
        @Override
        public Class<char[]> asClass() {
            return char[].class;
        }

        @Override
        public char[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            char[] array = new char[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readChar();
            return array;
        }

        @Override
        public void write(Buffer buffer, char[] array) {
            buffer.writeVarInt(array.length);
            for (char value : array) buffer.writeChar(value);
        }
    }

    record ShortType() implements BuiltinType, Type<Short> {
        @Override
        public Class<Short> asClass() {
            return Short.class;
        }

        @Override
        public Short read(Buffer buffer) {
            return buffer.readShort();
        }

        @Override
        public void write(Buffer buffer, Short value) {
            buffer.writeShort(value);
        }
    }

    record ShortArrayType() implements BuiltinType, Type<short[]> {
        @Override
        public Class<short[]> asClass() {
            return short[].class;
        }

        @Override
        public short[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            short[] array = new short[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readShort();
            return array;
        }

        @Override
        public void write(Buffer buffer, short[] array) {
            buffer.writeVarInt(array.length);
            for (short value : array) buffer.writeShort(value);
        }
    }

    record IntegerType() implements BuiltinType, Type<Integer> {
        @Override
        public Class<Integer> asClass() {
            return Integer.class;
        }

        @Override
        public Integer read(Buffer buffer) {
            return buffer.readInt();
        }

        @Override
        public void write(Buffer buffer, Integer value) {
            buffer.writeInt(value);
        }
    }

    record IntArrayType() implements BuiltinType, Type<int[]> {
        @Override
        public Class<int[]> asClass() {
            return int[].class;
        }

        @Override
        public int[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            int[] array = new int[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readInt();
            return array;
        }

        @Override
        public void write(Buffer buffer, int[] array) {
            buffer.writeVarInt(array.length);
            for (int value : array) buffer.writeInt(value);
        }
    }

    record LongType() implements BuiltinType, Type<Long> {
        @Override
        public Class<Long> asClass() {
            return Long.class;
        }

        @Override
        public Long read(Buffer buffer) {
            return buffer.readLong();
        }

        @Override
        public void write(Buffer buffer, Long value) {
            buffer.writeLong(value);
        }
    }

    record LongArrayType() implements BuiltinType, Type<long[]> {
        @Override
        public Class<long[]> asClass() {
            return long[].class;
        }

        @Override
        public long[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            long[] array = new long[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readLong();
            return array;
        }

        @Override
        public void write(Buffer buffer, long[] array) {
            buffer.writeVarInt(array.length);
            for (long value : array) buffer.writeLong(value);
        }
    }

    record FloatType() implements BuiltinType, Type<Float> {
        @Override
        public Class<Float> asClass() {
            return Float.class;
        }

        @Override
        public Float read(Buffer buffer) {
            return buffer.readFloat();
        }

        @Override
        public void write(Buffer buffer, Float value) {
            buffer.writeFloat(value);
        }
    }

    record FloatArrayType() implements BuiltinType, Type<float[]> {
        @Override
        public Class<float[]> asClass() {
            return float[].class;
        }

        @Override
        public float[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            float[] array = new float[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readFloat();
            return array;
        }

        @Override
        public void write(Buffer buffer, float[] array) {
            buffer.writeVarInt(array.length);
            for (float value : array) buffer.writeFloat(value);
        }
    }

    record DoubleType() implements BuiltinType, Type<Double> {
        @Override
        public Class<Double> asClass() {
            return Double.class;
        }

        @Override
        public Double read(Buffer buffer) {
            return buffer.readDouble();
        }

        @Override
        public void write(Buffer buffer, Double value) {
            buffer.writeDouble(value);
        }
    }

    record DoubleArrayType() implements BuiltinType, Type<double[]> {
        @Override
        public Class<double[]> asClass() {
            return double[].class;
        }

        @Override
        public double[] read(Buffer buffer) {
            int length = buffer.readVarInt();
            double[] array = new double[length];
            for (int i = 0; i < length; i++) array[i] = buffer.readDouble();
            return array;
        }

        @Override
        public void write(Buffer buffer, double[] array) {
            buffer.writeVarInt(array.length);
            for (double value : array) buffer.writeDouble(value);
        }
    }
}
