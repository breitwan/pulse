package pulse;

import java.io.Serial;

public class RemoteRuntimeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -1241844310925201070L;

    public RemoteRuntimeException() {
        super();
    }

    public RemoteRuntimeException(String message) {
        super(message);
    }

    public RemoteRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemoteRuntimeException(Throwable cause) {
        super(cause);
    }

    protected RemoteRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
