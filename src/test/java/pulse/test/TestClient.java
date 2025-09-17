package pulse.test;

import pulse.PulseClient;

import java.io.IOException;
import java.util.Arrays;

public final class TestClient {
    public static void main(String[] args) throws IOException {
        final PulseClient client = new PulseClient();

        client.connect(6969);
        Runtime.getRuntime().addShutdownHook(new Thread(client::close, "pulse-client-shutdown-hook"));

        var mathService = client.use(0x01, MathService.class);
        var stringService = client.use(0x02, StringService.class);

        var union = mathService.unite(-1, 2);
        System.out.println(Arrays.toString(union));

        var div = mathService.div(new DivideRequest(6, 2));
        System.out.println(div);

        var concatenated = stringService.concat("hello", "world");
        System.out.println(concatenated);

        client.close();
        client.await();
    }
}
