package pl.zapala.projekt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import pl.zapala.projekt.service.TimeCalculationStrategy;

import java.util.*;

class BFTStabilityTest {

    private static final Random random = new Random();
    private TimeCalculationStrategy weightedAverageStrategy;
    private Map<Integer, Double> satelliteWeights;

    @BeforeEach
    void setUp() {
        weightedAverageStrategy = new TimeCalculationStrategy.ByzantineFaultToleranceStrategy();

        satelliteWeights = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            satelliteWeights.put(i, 5.0 + random.nextInt(6)); // waga 5-10
        }
    }

    @Test
    @DisplayName("Test stabilności przy losowych awariach")
    void testStability() {
        final int ITERATIONS = 1000;
        Map<Integer, List<Double>> allResults = new LinkedHashMap<>();

        // Test liczby satelitów od 8 do 1
        for (int activeSatellites = 8; activeSatellites >= 1; activeSatellites--) {

            List<Double> deviations = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                long realTime = System.currentTimeMillis();

                List<Response> responses = generateSatelliteResponses(8, activeSatellites, realTime);

                long calculatedTime = weightedAverageStrategy.calculateTime(responses, satelliteWeights);

                deviations.add((double) Math.abs(realTime - calculatedTime));
            }

            allResults.put(activeSatellites, deviations);

        }

        System.out.println("WYNIKI:");

        for (int i = 8; i >= 1; i--) {
            List<Double> deviations = allResults.get(i);
            double avg = deviations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double stdDev = calculateStandard(deviations, avg);
            double min = deviations.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = deviations.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            System.out.printf("%d satelitów: Średnia = %.2f ms, Odch. std = %.2f ms, Min = %.2f ms, Max = %.2f ms, Próbek = %d%n",
                    i, avg, stdDev, min, max, deviations.size());
        }
        System.out.println("=".repeat(80));

    }

    private List<Response> generateSatelliteResponses(int totalSatellites, int activeSatellites, long realTime) {
        List<Response> responses = new ArrayList<>();

        // Losowo wybierz które satelity będą aktywne
        Set<Integer> activeIndices = new HashSet<>();
        while (activeIndices.size() < activeSatellites) {
            activeIndices.add(1 + random.nextInt(totalSatellites)); // ID satelitów od 1 do 8
        }

        for (int satelliteId : activeIndices) {
            // Odchylenie od -100ms do +100ms
            long timeOffset = -100 + random.nextInt(201);

            Response response = new Response(
                    satelliteId,
                    realTime + timeOffset,
                    ResponseStatus.OK,
                    "Time reported"
            );

            responses.add(response);
        }

        return responses;
    }

    private double calculateStandard(List<Double> values, double mean) {
        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }
}