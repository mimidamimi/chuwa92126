# Java Reactor Homework — Questions 1–10

## 1. Using the timeline of a request that calls a database and an external API, explain why the thread-per-request model wastes resources for I/O-bound work.

Answer: A traditional request thread might spend 5 ms validating input, 80 ms waiting for a database, 120 ms waiting for an external API, and 5 ms assembling a response. During the roughly 200 ms of I/O wait, the thread does little useful CPU work but still occupies stack memory and a thread-pool slot. Under high concurrency, many blocked threads increase memory consumption and context-switch overhead and can exhaust the pool. Nonblocking I/O frees the event-loop thread while waiting and resumes processing when responses arrive.

## 2. Describe the Reactor pattern: the roles of the demultiplexer, the event loop, the acceptor and the handlers. Explain why blocking an event loop thread is so harmful.

Answer: The demultiplexer (such as an OS selector) reports which sockets are ready. The event loop polls for events and dispatches them. The acceptor accepts new connections and registers them for processing. Handlers execute the relevant read, write, or application logic. One event-loop thread serves many connections, so blocking it on JDBC, sleep, or synchronous HTTP prevents it from servicing *all* those connections, increasing latency and reducing throughput.

## 3. Name the four interfaces of the Reactive Streams specification, describe the sequence of signals between a publisher and a subscriber, and explain the role of request(n).

Answer: The interfaces are `Publisher<T>`, `Subscriber<T>`, `Subscription`, and `Processor<T,R>`. The subscriber subscribes to the publisher; the publisher calls `onSubscribe(subscription)`; the subscriber calls `subscription.request(n)`; then the publisher may send up to `n` `onNext` items. It eventually sends one terminal `onComplete` or `onError`, or the subscriber may cancel. `request(n)` is the demand signal enabling backpressure: a compliant publisher must not emit more `onNext` items than requested. `Processor` is both a subscriber and a publisher.

## 4. Explain the difference between Mono and Flux, and what "nothing happens until you subscribe" means. Explain why Mono.just(buildReport()) behaves differently from Mono.fromCallable(() -> buildReport()).

Answer: `Mono<T>` represents zero or one item; `Flux<T>` represents zero to many items. Reactor pipelines are generally **lazy**: subscription triggers the upstream work and signal flow (unless a hot source is already active). However, Java evaluates arguments eagerly: `Mono.just(buildReport())` calls `buildReport()` immediately while assembling the pipeline and captures its result. `Mono.fromCallable(() -> buildReport())` defers execution until subscription and runs the callable for each subscription; exceptions become `onError` signals.

## 5. Compare map, flatMap, concatMap and flatMapSequential in terms of what the function returns, whether inner calls run concurrently, and the order of the output.

| Operator | Mapper returns | Inner publishers overlap? | Output order |
|---|---|---|---|
| `map` | Plain value `R` | No inner publisher | Preserves source order |
| `flatMap` | `Publisher<R>` | Yes (subject to concurrency limits) | May interleave / reorder |
| `concatMap` | `Publisher<R>` | No; subscribes sequentially | Preserves source order |
| `flatMapSequential` | `Publisher<R>` | Yes (prefetch/concurrency controlled) | Buffers to preserve source order |

Answer: Use `map` for synchronous transformations, `flatMap` for asynchronous work where completion order is acceptable, `concatMap` for strict sequential execution, and `flatMapSequential` when you want concurrent work but ordered output.

## 6. Explain why a try/catch around the code that builds a pipeline does not catch errors from that pipeline, and describe what onErrorReturn, onErrorResume, onErrorMap, retryWhen and timeout do.

Answer: Most pipeline work happens asynchronously **after subscription**, so a `try/catch` around assembly cannot catch later `onError` signals. `onErrorReturn` replaces an error with a fixed fallback value; `onErrorResume` switches to a fallback publisher; `onErrorMap` transforms an error; `retryWhen` resubscribes according to a retry strategy (potentially repeating side effects); `timeout` emits a timeout error if signals do not arrive within the configured limit (or switches to a configured fallback). Handle errors within the reactive chain, or at its subscriber boundary.

## 7. Explain the difference between publishOn and subscribeOn, and which scheduler should be used to wrap a blocking JDBC call.

Answer: `subscribeOn(scheduler)` influences where subscription and upstream source work run; `publishOn(scheduler)` switches execution context for downstream signals and operators until another switch. To isolate a blocking JDBC call, use `Mono.fromCallable(() -> jdbcCall()).subscribeOn(Schedulers.boundedElastic())`. `boundedElastic` is intended for bounded blocking workloads; avoid blocking Netty event loops or `Schedulers.parallel()`.

## 8. Explain what backpressure is, how a subscriber expresses demand, and the options available when a source such as Flux.interval cannot slow down.

Answer:Backpressure prevents fast producers from overwhelming slow consumers. A subscriber signals demand with `Subscription.request(n)` (often via Reactor's `BaseSubscriber` or operator-managed requests). A non-pausable source such as `Flux.interval` can fail with an overflow error when downstream has insufficient demand. Depending on semantics, use `onBackpressureBuffer` (with bounded capacity and an overflow policy), `onBackpressureDrop`, `onBackpressureLatest`, or sampling/throttling. Choose consciously: buffering uses memory, while dropping/latest sacrifices events. `onBackpressureError` makes overload explicit.

## 9. Explain the difference between cold and hot publishers, why subscribing twice to the same WebClient Mono sends two HTTP requests, and how cache() and share() change this.

Answer: A **cold** publisher starts a separate production sequence for each subscriber; a **hot** publisher can emit independently of individual subscriptions and may share one sequence. A typical `WebClient` request `Mono` is cold, so two subscriptions trigger two HTTP calls. `cache()` shares and replays the cached terminal result (including an error or empty completion) to later subscribers; consider TTL to avoid stale results. `share()` lets concurrent subscribers share one upstream subscription and, for a `Mono`, can also replay its terminal result to later subscribers; it does not provide the same explicit TTL/invalidation controls as `cache()`.

## 10. Compare Spring MVC and Spring WebFlux, and explain when Spring MVC with virtual threads is a better choice than WebFlux.

Answer: Spring MVC primarily uses the Servlet programming model and is a natural fit for imperative controllers and blocking dependencies. Spring WebFlux supports reactive `Mono`/`Flux` APIs and nonblocking I/O, allowing a small number of event-loop threads to handle many concurrent I/O operations when the full stack is nonblocking. Spring MVC with Java virtual threads can be preferable for a mostly blocking JDBC/JPA or synchronous-client application: it preserves readable imperative code while reducing the cost of waiting threads. WebFlux is often better for high-concurrency streaming, reactive composition, and end-to-end nonblocking clients. Virtual threads do not make blocking operations nonblocking and still require database connection-pool and downstream capacity limits.

---
