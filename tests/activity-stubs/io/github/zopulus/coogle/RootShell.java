package io.github.zopulus.coogle;
final class RootShell {
    enum Reason { AUTHORIZATION, UNAVAILABLE, TIMEOUT, EXECUTION }
    static final class Failure extends java.io.IOException { final Reason reason = Reason.EXECUTION; }
    static java.util.concurrent.CountDownLatch restarted = new java.util.concurrent.CountDownLatch(1);
    static String run(String command, int timeout) { restarted.countDown(); return ""; }
}
