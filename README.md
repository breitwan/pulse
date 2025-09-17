# Overview

**Pulse** is a lightweight RPC framework built on **Java 24+** features, including [Virtual Threads](https://openjdk.org/jeps/444), the [Foreign Function & Memory API](https://openjdk.org/jeps/454), and the [Class-File API](https://openjdk.org/jeps/484). Its goal is to make remote method calls feel as natural and efficient as local ones — without annotation processors, reflection, or complex build steps.

Under the hood, Pulse uses `java.lang.foreign.MemorySegment` for off-heap memory management to reduce GC pressure, `pulse.util.ObjectPool` to avoid unnecessary allocations in performance-critical paths and `java.lang.classfile` to generate RPC stubs Just In Time. On the server side, each request runs in its own virtual thread, enabling massive concurrency with low overhead, while client calls run directly in the caller’s thread for simplicity.

> **Note**: Pulse is an early-stage experiment and may never reach a production-ready state.  It is, however, a demonstration of what modern Java makes possible.

# Quick Start

Here’s what using Pulse looks like in practice:

```java
final PulseServer server = new PulseServer(6969);
server.export(0x01, MathService.class, MathServiceImpl::new);
server.run();

final PulseClient client = new PulseClient();
client.connect(6969);

final MathService service = client.use(0x01, MathService.class);
System.out.println(service.sum(2, 3)); // 5
```

# Usage

### 1. Define a Service Interface

```java
public interface MathService {
    int sum(int a, int b);
}
```

### 2. Implement the Service

```java
public final class MathServiceImpl implements MathService {
    @Override
    public int sum(int a, int b) {
        return a + b;
    }
}
```

### 3. Set up the Server

```java
final PulseServer server = new PulseServer(6969);
server.export(0x01, MathService.class, MathServiceImpl::new);

server.run();
Runtime.getRuntime().addShutdownHook(new Thread(server::close, "pulse-server-shutdown-hook"));
server.await();
```

### 4. Set up the Client

```java
final PulseClient client = new PulseClient();
client.connect(6969);
Runtime.getRuntime().addShutdownHook(new Thread(client::close, "pulse-client-shutdown-hook"));

final MathService service = client.use(0x01, MathService.class);
int result = service.sum(10, 20);
System.out.println(result); // 30

client.close();
client.await();
```

# Custom Data Types

Pulse supports custom data types through serialization logic defined with `pulse.network.Type<T>`.

### In-Class Serialization

Define a public `NETWORK_TYPE` constant inside your class for automatic detection:

```java
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
```

### Third-Party Classes

For classes you cannot modify, register a `Type<T>` manually:

```java
import thirdparty.Point;

public final class ThirdPartyType {
    public static final Type<Point> POINT = Type.of(
            Point.class, // Point(int x, int y)
            buffer -> new Point(buffer.readInt(), buffer.readInt()),
            (buffer, point) -> {
                buffer.writeInt(point.x());
                buffer.writeInt(point.y());
            }
    );
}
```

Register the type on both client and server:

```java
final PulseClient client = new PulseClient();
client.registerType(ThirdPartyType.POINT);

final PulseServer server = new PulseServer(6969);
server.registerType(ThirdPartyType.POINT);
```

# Contributing
Pulse is primarily a proof of concept, and active contributions aren’t being sought right now. If you’d like to experiment or have ideas to discuss, please open an issue on the [GitHub repository](https://github.com/breitwan/pulse/issues).

# Credits
* [Minestom](https://github.com/Minestom/Minestom) for inspiration and parts of the networking layer.

# License
This project is licensed under the [MIT License](LICENSE).