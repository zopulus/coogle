package io.github.zopulus.coogle;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded root execution for explicit controls and known companion status checks. */
final class RootShell {
    private static final String READY = "__COOGLE_ROOT_READY__\n";
    enum Reason { AUTHORIZATION, UNAVAILABLE, TIMEOUT, EXECUTION }
    static final class Failure extends IOException {
        final Reason reason;
        Failure(Reason reason) {
            super(switch (reason) {
                case AUTHORIZATION -> "请在 KernelSU 中授予 Root 权限后重试。";
                case UNAVAILABLE -> "未检测到可用的 Root 环境，请检查 KernelSU 是否正常运行。";
                case TIMEOUT -> "操作超时，请重试。";
                case EXECUTION -> "操作执行失败，请检查系统支持情况。";
            });
            this.reason = reason;
        }
    }
    static boolean commandNotStarted(Throwable failure) {
        return failure instanceof Failure root && (root.reason == Reason.AUTHORIZATION || root.reason == Reason.UNAVAILABLE);
    }
    static String run(String command, int timeout) throws Exception {
        Process process;
        try {
            // A successful root handshake separates su denial from a failed privileged command.
            process = new ProcessBuilder("su", "-c", "[ \"$(id -u)\" = 0 ] || exit 1; "
                    + "printf '__COOGLE_ROOT_READY__\\n'; " + command).redirectErrorStream(true).start();
        } catch (IOException unavailable) {
            throw new Failure(Reason.UNAVAILABLE);
        }
        return read(process, timeout);
    }
    static String read(Process process, int timeout) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        java.util.concurrent.atomic.AtomicReference<IOException> readFailure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread drain = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) >= 0)
                    if (output.size() < 65536) output.write(buffer, 0, Math.min(n, 65536 - output.size()));
            } catch (IOException failure) { readFailure.set(failure); }
        }, "Coogle-root-output");
        drain.setDaemon(true);
        drain.start();
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly(); drain.join(1000); throw new Failure(Reason.TIMEOUT);
        }
        drain.join(1000);
        if (drain.isAlive()) {
            process.getInputStream().close();
            throw new Failure(Reason.EXECUTION);
        }
        String result = output.toString(StandardCharsets.UTF_8);
        if (readFailure.get() != null) throw new Failure(Reason.EXECUTION);
        int ready = result.indexOf(READY);
        if (ready < 0) throw new Failure(Reason.AUTHORIZATION);
        if (process.exitValue() != 0) throw new Failure(Reason.EXECUTION);
        return result.substring(ready + READY.length());
    }
    private RootShell() { }
}
