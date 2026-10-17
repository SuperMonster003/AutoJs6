package org.autojs.autojs.model.explorer;

import java.io.File;
import java.util.Arrays;

/** A real subprocess catches pipe deadlocks and truncation that mocked streams cannot detect. */
public final class PrivilegedExplorerProcessFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "both":
                Thread errors = new Thread(() -> {
                    byte[] buffer = new byte[32 * 1024];
                    Arrays.fill(buffer, (byte) 'e');
                    System.err.write(buffer, 0, buffer.length);
                    System.err.flush();
                });
                errors.start();
                byte[] output = new byte[128 * 1024];
                Arrays.fill(output, (byte) 'o');
                System.out.write(output);
                System.out.flush();
                errors.join();
                break;
            case "overflow":
                byte[] buffer = new byte[8192];
                for (int i = 0; i < 1152; i++) System.out.write(buffer);
                System.out.flush();
                break;
            case "wait":
                Thread.sleep(10_000L);
                break;
            case "inherited-pipes":
                new ProcessBuilder(
                        new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                        "-cp", new File(PrivilegedExplorerProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath(),
                        PrivilegedExplorerProcessFixture.class.getName(), "hold-pipes"
                ).inheritIO().start();
                Thread.sleep(10_000L);
                break;
            case "hold-pipes":
                Thread.sleep(3000L);
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }
    }
}
