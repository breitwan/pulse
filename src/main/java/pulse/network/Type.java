package pulse.network;

import java.util.function.Function;

public interface Type<T> extends Buffer.Reader<T>, Buffer.Writer<T> {
    static <T> Type<T> of(Class<T> type, Buffer.Reader<T> reader, Buffer.Writer<T> writer) {
        return new Type<>() {
            @Override
            public Class<T> asClass() {
                return type;
            }

            @Override
            public T read(Buffer buffer) {
                return reader.read(buffer);
            }

            @Override
            public void write(Buffer buffer, T value) {
                writer.write(buffer, value);
            }
        };
    }

    Class<T> asClass();

    default <S> Type<S> transform(Function<T, S> to, Function<S, T> from) {
        return new BuiltinType.TransformType<>(this, to, from);
    }

    default Type<T> optional() {
        return new BuiltinType.OptionalType<>(this);
    }
}
