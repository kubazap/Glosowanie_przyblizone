package pl.zapala.projekt.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Process Launcher - Automatically spawns 8 satellite client processes.
 * Each satellite runs as a separate JVM instance.
 */
@Component
public class ProcessLauncher {

    private static final int SATELLITE_COUNT = 8;
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9000;

    private final List<Process> satelliteProcesses = new ArrayList<>();
    private boolean autoLaunch = true; // Set to false to disable auto-launch

    @PostConstruct
    public void launchSatellites() {
        if (!autoLaunch) {
            System.out.println("[ProcessLauncher] Auto-launch disabled");
            return;
        }

        System.out.println("[ProcessLauncher] Starting " + SATELLITE_COUNT + " satellite processes...");

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
                System.out.println("[ProcessLauncher] Satellite-" + i + " launched (PID: " +
                        process.pid() + ")");

                // Small delay between launches
                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("[ProcessLauncher] Failed to launch Satellite-" + i + ": " +
                        e.getMessage());
            }
        }

        System.out.println("[ProcessLauncher] All satellites launched successfully");
    }

    /**
     * Launch a single satellite process
     */
    private Process launchSatellite(int satelliteId) throws IOException {
        // Get Java home
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        // Get classpath from current JVM
        String classpath = System.getProperty("java.class.path");

        // Build command
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add("pl.zapala.projekt.satellite.SatelliteApp");
        command.add(String.valueOf(satelliteId));
        command.add(SERVER_HOST);
        command.add(String.valueOf(SERVER_PORT));

        // Create process builder
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        // Start process
        Process process = builder.start();

        // Start thread to read process output
        Thread outputThread = new Thread(() -> readProcessOutput(satelliteId, process));
        outputThread.setDaemon(true);
        outputThread.start();

        return process;
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
