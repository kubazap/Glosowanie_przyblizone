package pl.zapala.projekt.service;

import pl.zapala.projekt.model.VotingHistoryEntry;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Voting Service - Implements multiple time calculation algorithms.
 * Supports three strategies: Weighted Average, Median, and Byzantine Fault Tolerance.
 * Includes observability metrics and automatic ban mechanism for outliers.
 * Periodically polls satellites and calculates the most probable system time.
 */
@Service
public class VotingService {

    private final TcpServerService tcpServer;
    private final Map<Integer, SatelliteData> satelliteDataMap = new ConcurrentHashMap<>();
    private final Map<Integer, Double> satelliteWeights = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> errorCounters = new ConcurrentHashMap<>();

    // Observability metrics (Thread-Safe)
    private final AtomicInteger totalVotingRounds = new AtomicInteger(0);
    private final AtomicInteger totalNetworkErrors = new AtomicInteger(0);
    private final AtomicLong currentDeviation = new AtomicLong(0);

    // Voting history (last 50 entries)
    private final LinkedList<VotingHistoryEntry> votingHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 50;

    // Auto-ban threshold for time deviation
    private static final long AUTO_BAN_DEVIATION_THRESHOLD = 5000; // 5 seconds
    private static final int MAX_ERRORS_TOLERANCE = 3;

    private volatile long calculatedSystemTime = 0;
    private volatile long lastCalculationTime = 0;
    private volatile int activeResponseCount = 0;

    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

    /**
     * Available calculation strategies
     */
    public enum StrategyType {
        WEIGHTED_AVERAGE,
        MEDIAN,
        BYZANTINE_FAULT_TOLERANCE
    }

    // Current active strategy
    private TimeCalculationStrategy currentStrategy;
    private StrategyType currentStrategyType;

    // Available strategy instances
    private final Map<StrategyType, TimeCalculationStrategy> strategies = new EnumMap<>(StrategyType.class);

    public VotingService(TcpServerService tcpServer) {
        this.tcpServer = tcpServer;

        strategies.put(StrategyType.WEIGHTED_AVERAGE, new TimeCalculationStrategy.WeightedAverageStrategy());
        strategies.put(StrategyType.MEDIAN, new TimeCalculationStrategy.MedianStrategy());
        strategies.put(StrategyType.BYZANTINE_FAULT_TOLERANCE, new TimeCalculationStrategy.ByzantineFaultToleranceStrategy());

        // Set default strategy
        setStrategy(StrategyType.WEIGHTED_AVERAGE);

        initializeWeights();
    }

    /**
     * Change the calculation strategy at runtime
     */
    public void setStrategy(StrategyType strategyType) {
        this.currentStrategyType = strategyType;
        this.currentStrategy = strategies.get(strategyType);
        System.out.println("[Voting] Strategy changed to: " + currentStrategy.getName());
    }

    /**
     * Get current strategy type
     */
    public StrategyType getCurrentStrategyType() {
        return currentStrategyType;
    }

    /**
     * Get current strategy name
     */
    public String getCurrentStrategyName() {
        return currentStrategy.getName();
    }

    /**
     * Get current strategy description
     */
    public String getCurrentStrategyDescription() {
        return currentStrategy.getDescription();
    }

    /**
     * Get all available strategies
     */
    public Map<StrategyType, TimeCalculationStrategy> getAllStrategies() {
        return new EnumMap<>(strategies);
    }

    /**
     * Initialize default weights for all satellites (equal weight = 1.0)
     */
    private void initializeWeights() {
        for (int i = 1; i <= 8; i++) {
            satelliteWeights.put(i, 1.0);
            errorCounters.put(i, 0);
        }
    }

    /**
     * Scheduled task: Poll all satellites for time and calculate using current strategy.
     * Runs every 3 seconds with metrics collection and auto-ban mechanism.
     */
    @Scheduled(fixedRate = 3000, initialDelay = 1000)
    public void pollSatellitesAndCalculate() {
        try {
            totalVotingRounds.incrementAndGet();

            Request timeRequest = new Request(RequestType.GET_TIME, null);
            Map<Integer, CompletableFuture<Response>> futures = tcpServer.broadcastRequest(timeRequest);

            if (futures.isEmpty()) {
                return;
            }

            List<Response> responses = new ArrayList<>();

            futures.forEach((id, future) -> {
                try {
                    int waited = 0;
                    while (!future.isDone() && waited < 1000) {
                        Thread.sleep(10);
                        waited += 10;
                    }

                    Response response = future.getNow(null);

                    if (response == null) {
                        throw new Exception("Timeout");
                    }

                    responses.add(response);

                    SatelliteData data = satelliteDataMap.computeIfAbsent(id, k -> new SatelliteData(id));
                    data.setLastResponse(response);
                    data.setLastSeen(System.currentTimeMillis());

                    // Auto-ban: Check time deviation
                    long satelliteDeviation = Math.abs(response.getTimestamp() - System.currentTimeMillis());
                    if (satelliteDeviation > AUTO_BAN_DEVIATION_THRESHOLD) {
                        double currentWeight = satelliteWeights.get(id);
                        if (currentWeight > 0) {
                            satelliteWeights.put(id, 0.0);
                            System.out.println("[Voting] AUTO-BAN: Satellite-" + id +
                                    " deviation=" + satelliteDeviation + "ms > threshold=" +
                                    AUTO_BAN_DEVIATION_THRESHOLD + "ms. Weight set to 0.0");
                        }
                    }

                    errorCounters.put(id, 0);
                    data.setConnected(true);

                } catch (Exception e) {
                    totalNetworkErrors.incrementAndGet();
                    handleCommunicationError(id);
                }
            });

            activeResponseCount = responses.size();

            if (responses.isEmpty()) {
                System.out.println("[Voting] No satellite responses received");
                return;
            }

            // Use current strategy to calculate time
            calculatedSystemTime = currentStrategy.calculateTime(responses, satelliteWeights);
            lastCalculationTime = System.currentTimeMillis();

            long deviation = calculatedSystemTime - lastCalculationTime;
            currentDeviation.set(deviation);

            addToHistory(new VotingHistoryEntry(
                    timeFormatter.format(new Date(calculatedSystemTime)),
                    calculatedSystemTime,
                    activeResponseCount,
                    deviation
            ));

            System.out.printf("[Voting] Round: %d | Strategy: %s | System Time: %d | Deviation: %+d ms | Active: %d/8 | Network Errors: %d%n",
                    totalVotingRounds.get(), currentStrategy.getName(), calculatedSystemTime, deviation,
                    activeResponseCount, totalNetworkErrors.get());

        } catch (Exception e) {
            System.err.println("[Voting] Error: " + e.getMessage());
            totalNetworkErrors.incrementAndGet();
        }
    }

    /**
     * Add entry to voting history (FIFO, max 50 entries)
     */
    private synchronized void addToHistory(VotingHistoryEntry entry) {
        if (votingHistory.size() >= MAX_HISTORY_SIZE) {
            votingHistory.removeFirst();
        }
        votingHistory.addLast(entry);
    }

    /**
     * Get voting history (copy for thread safety)
     */
    public synchronized List<VotingHistoryEntry> getVotingHistory() {
        return new ArrayList<>(votingHistory);
    }

    /**
     * Handle communication error with satellite
     */
    private void handleCommunicationError(int id) {
        SatelliteData data = satelliteDataMap.get(id);
        if (data == null) return;

        int currentErrors = errorCounters.getOrDefault(id, 0) + 1;
        errorCounters.put(id, currentErrors);

        if (currentErrors >= MAX_ERRORS_TOLERANCE) {
            if (data.isConnected()) {
                System.out.println("[Voting] Satellite " + id + " marked as DISCONNECTED after " +
                        currentErrors + " failures.");
            }
            data.setConnected(false);
        }
    }

    public int getTotalVotingRounds() {
        return totalVotingRounds.get();
    }

    public int getTotalNetworkErrors() {
        return totalNetworkErrors.get();
    }

    public long getCurrentDeviation() {
        return currentDeviation.get();
    }

    /**
     * Update weight for a specific satellite
     */
    public void updateSatelliteWeight(int satelliteId, double weight) {
        if (weight < 0 || weight > 10) {
            throw new IllegalArgumentException("Weight must be between 0 and 10");
        }
        satelliteWeights.put(satelliteId, weight);
        System.out.println("[Voting] Updated weight for Satellite-" + satelliteId + " to " + weight);
    }

    /**
     * Get current weight for a satellite
     */
    public double getSatelliteWeight(int satelliteId) {
        return satelliteWeights.getOrDefault(satelliteId, 1.0);
    }

    /**
     * Get state of all satellites for UI display
     */
    public List<SatelliteState> getAllSatelliteStates() {
        List<SatelliteState> states = new ArrayList<>();

        for (int id = 1; id <= 8; id++) {
            SatelliteData data = satelliteDataMap.get(id);
            boolean isAlive = (data != null && data.isConnected());

            if (data != null && data.getLastResponse() != null) {
                states.add(new SatelliteState(
                        id,
                        data.getLastSeen(),
                        data.getLastResponse().getTimestamp(),
                        data.getLastResponse().getStatus(),
                        satelliteWeights.getOrDefault(id, 1.0),
                        isAlive
                ));
            } else {
                states.add(new SatelliteState(
                        id,
                        0,
                        0,
                        ResponseStatus.ERROR,
                        satelliteWeights.getOrDefault(id, 1.0),
                        isAlive
                ));
            }
        }

        return states;
    }

    /**
     * Get calculated system time
     */
    public long getCalculatedSystemTime() {
        return calculatedSystemTime;
    }

    /**
     * Get deviation from actual system time
     */
    public long getDeviation() {
        return calculatedSystemTime - System.currentTimeMillis();
    }

    /**
     * Get number of active satellites in last poll
     */
    public int getActiveResponseCount() {
        return activeResponseCount;
    }

    /**
     * Inject error into a specific satellite
     */
    public CompletableFuture<Response> injectError(int satelliteId, RequestType errorType, Long parameter) {
        Request request = new Request(errorType, parameter);
        return tcpServer.sendRequest(satelliteId, request);
    }

    /**
     * Reset all errors for a satellite
     */
    public CompletableFuture<Response> resetSatelliteErrors(int satelliteId) {
        errorCounters.put(satelliteId, 0);
        satelliteWeights.put(satelliteId, 1.0);

        Request request = new Request(RequestType.RESET_ERRORS, null);
        return tcpServer.sendRequest(satelliteId, request);
    }

    /**
     * Internal class to track satellite data
     */
    private static class SatelliteData {
        private final int id;
        private Response lastResponse;
        private long lastSeen;
        private boolean connected;

        public SatelliteData(int id) {
            this.id = id;
        }

        public Response getLastResponse() {
            return lastResponse;
        }

        public void setLastResponse(Response lastResponse) {
            this.lastResponse = lastResponse;
        }

        public long getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(long lastSeen) {
            this.lastSeen = lastSeen;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }
    }
}