package pl.zapala.projekt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import pl.zapala.projekt.service.TimeCalculationStrategy;

import java.util.*;
import java.util.stream.Collectors;

class AlgorithmsTest {

    private static final Random random = new Random();
    private Map<String, TimeCalculationStrategy> strategies;
    private Map<Integer, Double> satelliteWeights;

    @BeforeEach
    void setUp() {
        strategies = new LinkedHashMap<>();
        strategies.put("Średnia Ważona", new TimeCalculationStrategy.WeightedAverageStrategy());
        strategies.put("Mediana", new TimeCalculationStrategy.MedianStrategy());
        strategies.put("Byzantine FT", new TimeCalculationStrategy.ByzantineFaultToleranceStrategy());

        satelliteWeights = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            satelliteWeights.put(i, 5.0 + random.nextInt(6)); // waga 5-10
        }
    }

    @Test
    @DisplayName("Test odporności na duże odchylenia czasowe")
    void testResilienceToLargeDeviations() {
        System.out.println("TEST ODPORNOŚCI NA DUŻE ODCHYLENIA CZASOWE");
        System.out.println("Odchylenia: +1000ms do +4000ms na losowych serwerach\n");


        final int ITERATIONS = 1000;
        final int TOTAL_SATELLITES = 8;

        // Test od 1 do 7 serwerów z dużymi odchyleniami
        for (int faultyCount = 0; faultyCount <= 7; faultyCount++) {
            Map<String, List<Double>> strategyResults = new LinkedHashMap<>();

            // Test dla każdej strategii
            for (Map.Entry<String, TimeCalculationStrategy> strategyEntry : strategies.entrySet()) {
                String strategyName = strategyEntry.getKey();
                TimeCalculationStrategy strategy = strategyEntry.getValue();

                List<Double> deviations = new ArrayList<>();

                for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                    long realTime = System.currentTimeMillis();

                    // Generuj odpowiedzi z określoną liczbą serwerów z dużymi odchyleniami
                    List<Response> responses = generateResponsesWithLargeDeviations(
                            TOTAL_SATELLITES,
                            faultyCount,
                            realTime
                    );

                    long calculatedTime = strategy.calculateTime(responses, satelliteWeights);
                    deviations.add((double) Math.abs(realTime - calculatedTime));
                }

                strategyResults.put(strategyName, deviations);
            }

            // Wyświetl wyniki dla tego scenariusza
            printComparisonResults(faultyCount, strategyResults);
        }
    }

    /**
     * Generuje odpowiedzi z określoną liczbą serwerów z dużymi odchyleniami
     */
    private List<Response> generateResponsesWithLargeDeviations(int totalSatellites,
                                                                int faultyCount,
                                                                long realTime) {
        List<Response> responses = new ArrayList<>();

        // Wybierz losowe serwery które będą miały duże odchylenia
        Set<Integer> faultyIndices = new HashSet<>();
        while (faultyIndices.size() < faultyCount) {
            faultyIndices.add(1 + random.nextInt(totalSatellites));
        }

        // Generuj odpowiedzi dla wszystkich satelitów
        for (int satelliteId = 1; satelliteId <= totalSatellites; satelliteId++) {
            long timeOffset;

            if (faultyIndices.contains(satelliteId)) {
                // Serwer z dużym odchyleniem: +1000ms do +4000ms
                timeOffset = 1000 + random.nextInt(3001);
            } else {
                // Prawidłowy serwer: -10ms do +10ms
                timeOffset = -10 + random.nextInt(21);
            }

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

    /**
     * Wyświetl wyniki porównawcze dla danego scenariusza
     */
    private void printComparisonResults(int faultyCount, Map<String, List<Double>> results) {
        System.out.printf("\nWyniki dla %d serwerów z odchyleniami:\n", faultyCount);
        System.out.println("-".repeat(80));
        System.out.printf("%-20s | %-12s | %-12s | %-12s | %-12s%n",
                "Strategia", "Średnia (ms)", "Odch.std (ms)", "Min (ms)", "Max (ms)");
        System.out.println("-".repeat(80));

        // Wymuszona kolejność: Średnia → Mediana → BFT
        List<String> fixedOrder = List.of(
                "Średnia Ważona",
                "Mediana",
                "Byzantine FT"
        );

        for (String strategyName : fixedOrder) {
            List<Double> deviations = results.get(strategyName);
            if (deviations == null) continue;

            double avg = deviations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double stdDev = calculateStandard(deviations, avg);
            double min = deviations.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = deviations.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            System.out.printf("%-20s | %12.2f | %12.2f | %12.2f | %12.2f%n",
                    strategyName, avg, stdDev, min, max);
        }
    }


    private double calculateStandard(List<Double> values, double mean) {
        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }
}