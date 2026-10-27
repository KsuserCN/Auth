package cn.ksuser.api.support;

import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/** Executes production Lua against a temporary, local Redis; never connects to configured application Redis. */
public final class LocalRedisServer implements AutoCloseable {
    private final Process process;
    private final Path directory;
    private final LettuceConnectionFactory connections;
    private final StringRedisTemplate template;

    private LocalRedisServer(Process process, Path directory, int port) {
        this.process = process;
        this.directory = directory;
        this.connections = new LettuceConnectionFactory("127.0.0.1", port);
        connections.afterPropertiesSet();
        connections.start();
        this.template = new StringRedisTemplate(connections);
    }

    public static LocalRedisServer start() throws Exception {
        String executable = System.getenv("KSUSER_TEST_REDIS_SERVER");
        Assumptions.assumeTrue(executable != null && !executable.isBlank(),
            "Set KSUSER_TEST_REDIS_SERVER to execute isolated Redis Lua integration tests");
        if (!Files.isExecutable(Path.of(executable))) throw new IOException("Test Redis binary is not executable");
        Path directory = Files.createTempDirectory("ksuser-redis-security-test-");
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        Process process = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", Integer.toString(port),
            "--save", "", "--appendonly", "no", "--protected-mode", "yes", "--dir", directory.toString())
            .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        LocalRedisServer server = new LocalRedisServer(process, directory, port);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        Exception last = null;
        while (process.isAlive() && System.nanoTime() < deadline) {
            try (var connection = server.connections.getConnection()) {
                if ("PONG".equals(connection.ping())) return server;
            } catch (Exception ex) { last = ex; }
            Thread.sleep(20);
        }
        server.close();
        throw new IOException("Temporary test Redis did not start", last);
    }

    public StringRedisTemplate template() { return template; }

    @Override public void close() throws Exception {
        connections.destroy();
        process.destroy();
        if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(3, TimeUnit.SECONDS); }
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
