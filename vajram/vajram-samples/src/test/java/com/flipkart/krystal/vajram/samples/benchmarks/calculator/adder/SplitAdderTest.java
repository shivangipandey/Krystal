package com.flipkart.krystal.vajram.samples.benchmarks.calculator.adder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.flipkart.krystal.vajram.VajramID.vajramID;
import static com.flipkart.krystal.vajram.samples.Util.javaFuturesBenchmark;
import static com.flipkart.krystal.vajram.samples.Util.javaMethodBenchmark;
import static com.flipkart.krystal.vajram.samples.Util.printStats;
import static com.flipkart.krystal.vajram.samples.benchmarks.calculator.adder.Adder.add;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flipkart.krystal.krystex.decoration.MainLogicDecoratorConfig;
import com.flipkart.krystal.krystex.decorators.observability.DefaultNodeExecutionReport;
import com.flipkart.krystal.krystex.decorators.observability.MainLogicExecReporter;
import com.flipkart.krystal.krystex.decorators.observability.NodeExecutionReport;
import com.flipkart.krystal.krystex.node.KrystalNodeExecutor;
import com.flipkart.krystal.krystex.node.KrystalNodeExecutorMetrics;
import com.flipkart.krystal.vajram.samples.benchmarks.calculator.adder.ChainAdderTest.RequestContext;
import com.flipkart.krystal.vajramexecutor.krystex.KrystexVajramExecutor;
import com.flipkart.krystal.vajramexecutor.krystex.VajramNodeGraph;
import com.flipkart.krystal.vajramexecutor.krystex.VajramNodeGraph.Builder;
import com.google.common.collect.ImmutableMap;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SplitAdderTest {
  private Builder graph;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    graph = loadFromClasspath("com.flipkart.krystal.vajram.samples.benchmarks.calculator");
    objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Test
  void splitAdder_success() throws Exception {
    CompletableFuture<Integer> future;
    NodeExecutionReport nodeExecutionReport = new DefaultNodeExecutionReport(Clock.systemUTC());
    MainLogicExecReporter mainLogicExecReporter = new MainLogicExecReporter(nodeExecutionReport);
    try (KrystexVajramExecutor<RequestContext> krystexVajramExecutor =
        graph
            .build()
            .createExecutor(
                new RequestContext(""),
                ImmutableMap.of(
                    mainLogicExecReporter.decoratorType(),
                    new MainLogicDecoratorConfig(
                        mainLogicExecReporter.decoratorType(),
                        logicExecutionContext -> true,
                        logicExecutionContext -> mainLogicExecReporter.decoratorType(),
                        decoratorContext -> mainLogicExecReporter)))) {
      future = executeVajram(krystexVajramExecutor, 0);
    }
    assertThat(future.get()).isEqualTo(55);
    System.out.println(
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodeExecutionReport));
  }

  @Test
  void vajram_benchmark() throws Exception {
    int loopCount = 50_000;
    VajramNodeGraph graph = this.graph.maxParallelismPerCore(1).build();
    long javaNativeTimeNs = javaMethodBenchmark(this::splitAdd, loopCount);
    long javaFuturesTimeNs = javaFuturesBenchmark(this::splitAddAsync, loopCount);
    //noinspection unchecked
    CompletableFuture<Integer>[] futures = new CompletableFuture[loopCount];
    KrystalNodeExecutorMetrics[] metrics = new KrystalNodeExecutorMetrics[loopCount];
    long startTime = System.nanoTime();
    long timeToCreateExecutors = 0;
    long timeToEnqueueVajram = 0;
    for (int value = 0; value < loopCount; value++) {
      long iterStartTime = System.nanoTime();
      try (KrystexVajramExecutor<RequestContext> krystexVajramExecutor =
          graph.createExecutor(new RequestContext(""))) {
        metrics[value] =
            ((KrystalNodeExecutor) krystexVajramExecutor.getKrystalExecutor())
                .getKrystalNodeMetrics();
        timeToCreateExecutors += System.nanoTime() - iterStartTime;
        long enqueueStart = System.nanoTime();
        futures[value] = executeVajram(krystexVajramExecutor, value);
        timeToEnqueueVajram += System.nanoTime() - enqueueStart;
      }
    }
    allOf(futures).join();
    long vajramTimeNs = System.nanoTime() - startTime;
    allOf(futures)
        .whenComplete(
            (unused, throwable) -> {
              for (int i = 0; i < futures.length; i++) {
                CompletableFuture<Integer> future = futures[i];
                assertThat(future.getNow(0)).isEqualTo((i * 100) + 55);
              }
            })
        .get();
    /*
     * Benchmark config:
     *    loopCount = 50_000
     *    maxParallelismPerCore = 0.5
     *    Processor: 2.6 GHz 6-Core Intel Core i7 (with hyperthreading - 12 virtual cores)
     * Benchmark result:
     *    platform overhead = ~300 µs per request
     *    maxPoolSize = 6
     *    maxActiveLeasesPerObject: 4114
     *    peakAvgActiveLeasesPerObject: 4110.5
     *    Avg. time to Enqueue vajrams: 7,289 ns
     *    Avg. time to execute vajrams: 358,405 ns
     *    Throughput executions/sec: 2900
     */
    printStats(
        loopCount,
        graph,
        javaNativeTimeNs,
        javaFuturesTimeNs,
        metrics,
        timeToCreateExecutors,
        timeToEnqueueVajram,
        vajramTimeNs);
  }

  private static CompletableFuture<Integer> executeVajram(
      KrystexVajramExecutor<RequestContext> krystexVajramExecutor, int multiplier) {
    return krystexVajramExecutor.execute(
        vajramID(SplitAdder.ID),
        rc ->
            SplitAdderRequest.builder()
                .numbers(
                    new ArrayList<>(
                        Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                            .map(integer -> integer + multiplier * 10)
                            .toList()))
                .build(),
        "chainAdderTest" + multiplier);
  }

  private void splitAdd(Integer value) {
    splitAdd(
        new ArrayList<>(
            Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .map(integer -> integer + value * 10)
                .toList()));
  }

  private int splitAdd(List<Integer> numbers) {
    if (numbers.isEmpty()) {
      return 0;
    } else if (numbers.size() == 1) {
      return add(numbers.get(0), 0);
    } else {
      int subListSize = numbers.size() / 2;
      return splitAdd(numbers.subList(0, subListSize))
          + splitAdd(numbers.subList(subListSize, numbers.size()));
    }
  }

  private CompletableFuture<Integer> splitAddAsync(Integer value) {
    return splitAddAsync(
        new ArrayList<>(
            Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .map(integer -> integer + value * 10)
                .toList()));
  }

  private CompletableFuture<Integer> splitAddAsync(List<Integer> numbers) {
    if (numbers.isEmpty()) {
      return completedFuture(0);
    } else if (numbers.size() == 1) {
      return addAsync(numbers.get(0), 0);
    } else {
      int subListSize = numbers.size() / 2;
      return splitAddAsync(numbers.subList(0, subListSize))
          .thenCombine(splitAddAsync(numbers.subList(subListSize, numbers.size())), Integer::sum);
    }
  }

  private CompletableFuture<Integer> addAsync(int a, int b) {
    return completedFuture(a + b);
  }

  private static Builder loadFromClasspath(String... packagePrefixes) {
    Builder builder = VajramNodeGraph.builder();
    Arrays.stream(packagePrefixes).forEach(builder::loadFromPackage);
    return builder;
  }
}
