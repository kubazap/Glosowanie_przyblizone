package pl.zapala.projekt.service;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Process Launcher - Automatically spawns 8 satellite client processes.
 * Each satellite runs as a separate JVM instance with HIGH CPU priority.
 */
@Component
public class ProcessLauncher {

    private static final int SATELLITE_COUNT = 8;
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9000;

    private static final String PRIORITY_HIGH = "HIGH";
    private static final String PRIORITY_REALTIME = "REALTIME";
    private static final String PRIORITY_ABOVE_NORMAL = "ABOVENORMAL";

    private final List<Process> satelliteProcesses = new ArrayList<>();
    private final List<Path> tempClasspathFiles = new ArrayList<>();
    private boolean autoLaunch = true; // Set to false to disable auto-launch

    @EventListener(ApplicationReadyEvent.class)
    public void launchSatellites() {
        if (!autoLaunch) {
            System.out.println("[ProcessLauncher] Auto-launch disabled");
            return;
        }

        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        System.out.println("[ProcessLauncher] Starting " + SATELLITE_COUNT + " satellite processes with HIGH priority...");

        // Delay to ensure TCP server is ready
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        for (int i = 1; i <= SATELLITE_COUNT; i++) {
            try {
                Process process = launchSatellite(i);
                satelliteProcesses.add(process);
                System.out.println("[ProcessLauncher] Satellite-" + i + " launched with HIGH priority (PID: " +
                        process.pid() + ")");

                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("[ProcessLauncher] Failed to launch Satellite-" + i + ": " +
                        e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("[ProcessLauncher] All satellites launched successfully");
    }

    /**
     * Launch a single satellite process with HIGH CPU priority
     */
    private Process launchSatellite(int satelliteId) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder builder;

        if (os.contains("win")) {
            builder = createWindowsProcessWithPriority(satelliteId);
        } else {
            builder = createUnixProcessWithPriority(satelliteId);
        }

        builder.redirectErrorStream(true);
        Process process = builder.start();

        try {
            setPriorityAfterStart(process);
        } catch (Exception e) {
            System.err.println("[ProcessLauncher] Could not set priority after start: " + e.getMessage());
        }

        Thread outputThread = new Thread(() -> readProcessOutput(satelliteId, process));
        outputThread.setDaemon(true);
        outputThread.start();

        return process;
    }

    /**
     * Create Windows process with HIGH priority using classpath file
     */
    private ProcessBuilder createWindowsProcessWithPriority(int satelliteId) throws IOException {
        // Get Java home
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java.exe";

        String classpath = System.getProperty("java.class.path");

        Path classpathFile = createClasspathFile(classpath, satelliteId);
        tempClasspathFiles.add(classpathFile);

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add("@" + classpathFile.toAbsolutePath().toString());
        command.add("pl.zapala.projekt.satellite.SatelliteApp");
        command.add(String.valueOf(satelliteId));
        command.add(SERVER_HOST);
        command.add(String.valueOf(SERVER_PORT));

        System.out.println("[ProcessLauncher] Command for Satellite-" + satelliteId + ": " +
                String.join(" ", command));

        return new ProcessBuilder(command);
    }

    /**
     * Create temporary classpath file
     */
    private Path createClasspathFile(String classpath, int satelliteId) throws IOException {
        Path tempFile = Files.createTempFile("satellite-" + satelliteId + "-cp-", ".txt");

        Files.writeString(tempFile, classpath);
        tempFile.toFile().deleteOnExit();

        return tempFile;
    }

    /**
     * Set process priority AFTER it starts using wmic command
     */
    private void setPriorityAfterStart(Process process) {
        try {
            long pid = process.pid();
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                String priority = "128";

                ProcessBuilder pb = new ProcessBuilder(
                        "wmic", "process", "where", "ProcessId=" + pid,
                        "CALL", "setpriority", priority
                );
                Process wmicProcess = pb.start();
                wmicProcess.waitFor();

                System.out.println("[ProcessLauncher] Set HIGH priority for PID: " + pid);
            }
        } catch (Exception e) {
            System.err.println("[ProcessLauncher] Could not set priority: " + e.getMessage());
        }
    }

    private ProcessBuilder createUnixProcessWithPriority(int satelliteId) {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        String classpath = System.getProperty("java.class.path");

        List<String> command = new ArrayList<>();
        command.add("nice");
        command.add("-n");
        command.add("-10");
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add("pl.zapala.projekt.satellite.SatelliteApp");
        command.add(String.valueOf(satelliteId));
        command.add(SERVER_HOST);
        command.add(String.valueOf(SERVER_PORT));

        return new ProcessBuilder(command);
    }

    /**
     * Read and log process output
     */
    private void readProcessOutput(int satelliteId, Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Satellite-" + satelliteId + " Process] " + line);
            }

        } catch (IOException e) {
            // Process terminated or error reading output
        }
    }

    @PreDestroy
    public void shutdownSatellites() {
        System.out.println("[ProcessLauncher] Shutting down satellite processes...");

        for (Process process : satelliteProcesses) {
            if (process.isAlive()) {
                process.destroy();

                try {
                    // Wait for process to terminate gracefully
                    long end = System.currentTimeMillis() + 5000;
                    while (process.isAlive() && System.currentTimeMillis() < end) {
                        Thread.sleep(100);
                    }
                    if (process.isAlive()) {
                        // Force kill if still alive
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }

        satelliteProcesses.clear();

        // Clean up temporary classpath files
        for (Path tempFile : tempClasspathFiles) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
        tempClasspathFiles.clear();

        System.out.println("[ProcessLauncher] All satellite processes terminated");
    }

    /**
     * Manually launch satellites (if auto-launch is disabled)
     */
    public void manualLaunch() {
        if (satelliteProcesses.isEmpty()) {
            launchSatellites();
        } else {
            System.out.println("[ProcessLauncher] Satellites already running");
        }
    }

    /**
     * Get count of running satellites
     */
    public int getRunningCount() {
        return (int) satelliteProcesses.stream()
                .filter(Process::isAlive)
                .count();
    }

    /**
     * Restart a specific satellite
     */
    public void restartSatellite(int satelliteId) {
        if (satelliteId < 1 || satelliteId > satelliteProcesses.size()) {
            System.err.println("[ProcessLauncher] Invalid satellite ID: " + satelliteId);
            return;
        }

        Process oldProcess = satelliteProcesses.get(satelliteId - 1);
        if (oldProcess != null && oldProcess.isAlive()) {
            oldProcess.destroy();
        }

        try {
            Process newProcess = launchSatellite(satelliteId);
            satelliteProcesses.set(satelliteId - 1, newProcess);
            System.out.println("[ProcessLauncher] Satellite-" + satelliteId + " restarted");
        } catch (IOException e) {
            System.err.println("[ProcessLauncher] Failed to restart Satellite-" + satelliteId +
                    ": " + e.getMessage());
        }
    }
}