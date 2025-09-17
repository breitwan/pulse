package pulse.test;

import pulse.PulseServer;

import java.io.IOException;

public final class TestServer {
    public static void main(String[] args) throws IOException {
        final PulseServer server = new PulseServer(6969);

        server.export(0x01, MathService.class, MathServiceImpl::new);
        server.export(0x02, StringService.class, StringServiceImpl::new);

        server.run();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "pulse-server-shutdown-hook"));
        server.await();
    }
}
