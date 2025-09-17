package pulse.test;

import pulse.network.Buffer;
import pulse.network.Type;

public record DivideRequest(int dividend, int divisor) {
    public static final Type<DivideRequest> NETWORK_TYPE =
            Type.of(DivideRequest.class, DivideRequest::new, DivideRequest::write);

    DivideRequest(Buffer buffer) {
        this(buffer.readInt(), buffer.readInt());
    }

    public DivideRequest {
        if (divisor == 0) {
            throw new IllegalArgumentException("Divisor cannot be zero.");
        }
    }

    static void write(Buffer buffer, DivideRequest request) {
        buffer.writeInt(request.dividend);
        buffer.writeInt(request.divisor);
    }
}