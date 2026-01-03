package pl.zapala.projekt.model;

/**
 * DTO storing information about a single voting cycle
 */
public record VotingHistoryEntry(
        String timeString,      // Formatted time HH:mm:ss.SSS
        long systemTime,        // Timestamp in ms
        int activeSatellites,   // Number of active satellites
        long deviation          // Deviation from actual time in ms
) {
    @Override
    public String toString() {
        return String.format("[%s] Active: %d, Deviation: %+d ms",
                timeString, activeSatellites, deviation);
    }
}