package pl.zapala.projekt.service;

import pl.zapala.projekt.protocol.SatelliteProtocol.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategies for calculating system time from satellite responses.
 * Each strategy implements a different algorithm for fault tolerance.
 */
public interface TimeCalculationStrategy {

    /**
     * Calculate system time from satellite responses
     * @param responses List of valid satellite responses
     * @param weights Map of satellite weights (ID -> weight)
     * @return Calculated system time in milliseconds
     */
    long calculateTime(List<Response> responses, Map<Integer, Double> weights);
    String getName();
    String getDescription();

    /**
     * Weighted Average Strategy - calculates time based on satellite weights.
     * Satellites with higher weights contribute more to the final result.
     */
    class WeightedAverageStrategy implements TimeCalculationStrategy {

        @Override
        public long calculateTime(List<Response> responses, Map<Integer, Double> weights) {
            if (responses.isEmpty()) {
                return System.currentTimeMillis();
            }

            List<Response> validResponses = responses.stream()
                    .filter(r -> r.getStatus() == ResponseStatus.OK)
                    .collect(Collectors.toList());

            if (validResponses.isEmpty()) {
                return System.currentTimeMillis();
            }

            double totalWeight = 0.0;
            double weightedSum = 0.0;

            for (Response response : validResponses) {
                double weight = weights.getOrDefault(response.getId(), 1.0);
                weightedSum += response.getTimestamp() * weight;
                totalWeight += weight;
            }

            if (totalWeight == 0) {
                return (long) validResponses.stream()
                        .mapToLong(Response::getTimestamp)
                        .average()
                        .orElse(System.currentTimeMillis());
            }

            return Math.round(weightedSum / totalWeight);
        }

        @Override
        public String getName() {
            return "Średnia Ważona";
        }

        @Override
        public String getDescription() {
            return "Oblicza czas na podstawie wag satelitów. Satelity o wyższej wadze mają większy wpływ na wynik.";
        }
    }

    /**
     * Median Strategy - uses the median value from all satellite times.
     * Immune to extreme outliers (both high and low).
     */
    class MedianStrategy implements TimeCalculationStrategy {

        @Override
        public long calculateTime(List<Response> responses, Map<Integer, Double> weights) {
            if (responses.isEmpty()) {
                return System.currentTimeMillis();
            }

            List<Long> times = responses.stream()
                    .filter(r -> r.getStatus() == ResponseStatus.OK)
                    .map(Response::getTimestamp)
                    .sorted()
                    .collect(Collectors.toList());

            if (times.isEmpty()) {
                return System.currentTimeMillis();
            }

            int size = times.size();
            if (size % 2 == 0) {
                // Even number: average of two middle values
                return (times.get(size / 2 - 1) + times.get(size / 2)) / 2;
            } else {
                // Odd number: middle value
                return times.get(size / 2);
            }
        }

        @Override
        public String getName() {
            return "Mediana";
        }

        @Override
        public String getDescription() {
            return "Wybiera medianę z czasów satelitów. Odporna na skrajne wartości odstające.";
        }
    }

    /**
     * Byzantine Fault Tolerance Strategy - removes outliers and medians the rest.
     * Discards satellites with times deviating significantly from the median.
     */
    class ByzantineFaultToleranceStrategy implements TimeCalculationStrategy {

        private static final double SIGMA_THRESHOLD = 2.0;

        @Override
        public long calculateTime(List<Response> responses, Map<Integer, Double> weights) {

            if (responses.isEmpty()) {
                return System.currentTimeMillis();
            }

            List<Long> times = responses.stream()
                    .filter(r -> r.getStatus() == ResponseStatus.OK)
                    .map(Response::getTimestamp)
                    .sorted()
                    .collect(Collectors.toList());

            if (times.isEmpty()) {
                return System.currentTimeMillis();
            }

            if (times.size() <= 2) {
                return times.stream()
                        .mapToLong(Long::longValue)
                        .sum() / times.size();
            }

            double median = calculateMedian(times);

            double variance = times.stream()
                    .mapToDouble(t -> Math.pow(t - median, 2))
                    .average()
                    .orElse(0);

            double stdDev = Math.sqrt(variance);

            List<Long> filteredTimes = times.stream()
                    .filter(t -> Math.abs(t - median) <= SIGMA_THRESHOLD * stdDev)
                    .collect(Collectors.toList());

            if (filteredTimes.isEmpty()) {
                filteredTimes = times;
            }

            return (long) calculateMedian(filteredTimes);
        }

        private double calculateMedian(List<Long> values) {
            int size = values.size();
            if (size % 2 == 0) {
                return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
            } else {
                return values.get(size / 2);
            }
        }

        @Override
        public String getName() {
            return "Byzantine Fault Tolerance";
        }

        @Override
        public String getDescription() {
            return "Używa mediany i odrzuca wartości odstające (>2σ). Bardzo wysoka odporność na awarie bizantyjskie.";
        }
    }
}