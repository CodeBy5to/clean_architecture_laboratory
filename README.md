# Clean Architecture Laboratory (Hexagonal Architecture, CircuitBreaker, FallBack, Webflux, WebClient, Sqs, Redis)
Proyecto Springboot con reactor y netty usando el plugin clean architecture de Bancolombia, implementando un adaptador de tipo webclient para consultar datos de la pokeApi agregando el patron circuitbreaker y fallBack. Adicional se implementan 2 entry points uno para exponer los servicios en rest y otro para crear un listener SQS. De igual manera de implementa reactive redis para manejar cache para la gran cantidad de datos.


## Pruebas con reactor
Se utiliza WebFlux para implementar programación reactiva y realizar consultas anidadas a la PokeAPI. En teoría, se realiza la primera petición para obtener un listado de Pokémon, y posteriormente, se encadenan otras peticiones para obtener los detalles de cada Pokémon de manera asíncrona y no bloqueante.

```cURL
curl  -X GET \
  'https://pokeapi.co/api/v2/pokemon?limit=1' \
  --header 'Accept: */*' \
  --header 'User-Agent: Thunder Client (https://www.thunderclient.com)'
```

limit vendria siendo la vairable de url para definir la maxima cantidad de registros que quiero listar, en este caso seria 1 con esta respuesta

```json
{
  "count": 1302,
  "next": "https://pokeapi.co/api/v2/pokemon?offset=1&limit=1",
  "previous": null,
  "results": [
    {
      "name": "bulbasaur",
      "url": "https://pokeapi.co/api/v2/pokemon/1/"
    }
  ]
}
```

Teniendo en cuenta el id del objeto con propiedad "url" vamos a llamar la otra peticion, la cual nos dará la información complementaria del objeto

```cURL
curl  -X GET \
  'https://pokeapi.co/api/v2/pokemon/1' \
  --header 'Accept: */*' \
  --header 'User-Agent: Thunder Client (https://www.thunderclient.com)'
```

Esto, nos dará una respuesta muy extensa de la información de un pókemon, por lo que solo colocaré el objeto con la información que necesito

```java
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Pokemon {

    private String name;
    private Integer weight;
    private Integer height;
    @JsonProperty("location_area_encounters")
    private String locationAreaEncounters;
    @JsonProperty("base_experience")
    private Integer baseExperience;
    private Integer id;

}
```

sabiendo el orden del flujo de los datos, se procede a implementar reactor y webclient para consultar la información de forma asincrona

creamos el modulo con el adaptador de restconsumer<br>
![image](https://github.com/user-attachments/assets/194606fe-69c1-419a-a885-4953b0681d3a)

y se procede a realizar la programacion declarativa
```java
    @Override
    @CircuitBreaker(name = "getAllPokemonsService", fallbackMethod = "fallbackMethod")
    public Flux<Pokemon> getAllPokemons(Integer limit) {
        return client.get()
                .uri("?limit={limit}", limit)
                .retrieve()
                .bodyToMono(PokemonResponse.class)
                .flatMapMany(response -> Flux.fromIterable(response.getResults())
                        .flatMap(reference -> getPokemonByUrl(reference.getUrl())));
    }

    @Override
    @CircuitBreaker(name = "getPokemonByUrlService", fallbackMethod = "fallbackMethod2")
    public Mono<Pokemon> getPokemonByUrl(String url) {
        return client.get()
                .uri("/{id}", url.split("/").length > 6 ? url.split("/")[6] : "0")
                .retrieve()
                .bodyToMono(Pokemon.class);
    }
```

### getAllPokemons(Integer limit);
Este método obtiene una lista de Pokémon, limitando el número de resultados según el parámetro limit.

- **client.get().uri("?limit={limit}", limit)**: Realiza una solicitud HTTP GET a una URI con el parámetro limit.
  
- **.retrieve().bodyToMono(PokemonResponse.class)**: Recupera la respuesta de la API y la convierte en un Mono<PokemonResponse>, donde PokemonResponse es una clase que contiene la lista de resultados de Pokémon.
  
- **.flatMapMany(response -> Flux.fromIterable(response.getResults())**: Convierte la lista de resultados (referencias a Pokémon) en un Flux<Pokemon>, es decir, un flujo reactivo de objetos Pokémon.
  
- **.flatMap(reference -> getPokemonByUrl(reference.getUrl()))**: Por cada referencia de Pokémon obtenida, llama al método getPokemonByUrl para obtener detalles completos de ese Pokémon usando la URL proporcionada.
  
Este método devuelve un Flux<Pokemon>, lo que significa que está devolviendo un flujo de Pokémon que se emiten a medida que la API responde.


## CircuitBreaker y fallBack
Se realiza una simple implementacion de circuitbreaker desde el adaptador del restconsumer, definiendo en el properties la configuracion y toleracia de los distintos circuitBreakers

```yaml
resilience4j:
  circuitbreaker:
    instances:
      getAllPokemonsService:
        registerHealthIndicator: true
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: "2s"
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowSize: 10
        minimumNumberOfCalls: 10
        waitDurationInOpenState: "10s"
      getPokemonByUrlService:
        registerHealthIndicator: true
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: "2s"
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowSize: 10
        minimumNumberOfCalls: 10
        waitDurationInOpenState: "10s"
```
Gracias a la aop y la anocacion intrinseca de @CircuitBreaker, se logra detectar las llamadas a la api, se pueden observarlas llamadas de los servicios y su estado en los logs
```txt
2024-09-17T12:21:40.923-05:00  INFO 6232 --- [clean_architecture_laboratory] [  restartedMain] com.github.codeby5to.MainApplication     : Started MainApplication in 4.648 seconds (process running for 5.088)
2024-09-17T12:21:56.765-05:00 DEBUG 6232 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : CircuitBreaker 'getAllPokemonsService' succeeded:
2024-09-17T12:21:56.767-05:00 DEBUG 6232 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event SUCCESS published: 2024-09-17T12:21:56.766164300-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a successful call. Elapsed time: 2574 ms
2024-09-17T12:22:01.872-05:00 DEBUG 6232 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : CircuitBreaker 'getAllPokemonsService' succeeded:
2024-09-17T12:22:01.872-05:00 DEBUG 6232 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event SUCCESS published: 2024-09-17T12:22:01.872133200-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a successful call. Elapsed time: 884 ms
2024-09-17T12:22:05.785-05:00 DEBUG 6232 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : CircuitBreaker 'getAllPokemonsService' succeeded:
2024-09-17T12:22:05.785-05:00 DEBUG 6232 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event SUCCESS published: 2024-09-17T12:22:05.785717600-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a successful call. Elapsed time: 183 ms

```

se evidencia el momento en que se cierra el circuitbreaker cuando muchas peticiones han sido enviadas con el servicio de la pokeApi abajo, se nota como cambia el log de pasar a lanzar excepciones a lo loco, a controlar la ejecución del servicio evitando la ejecucion de un fallo repetitivo

```txt
org.springframework.web.reactive.function.client.WebClientRequestException: Failed to resolve 'pokeap.co' [A(1)] after 2 queries 
	at org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9(ExchangeFunctions.java:136) ~[spring-webflux-6.1.12.jar:6.1.12]
	Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException: 
Error has been observed at the following site(s):
	*__checkpoint ⇢ Request to GET https://pokeap.co/api/v2/pokemon/ [DefaultWebClient]
Original Stack Trace:
		at org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9(ExchangeFunctions.java:136) ~[spring-webflux-6.1.12.jar:6.1.12]
		at reactor.core.publisher.MonoErrorSupplied.subscribe(MonoErrorSupplied.java:55) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.Mono.subscribe(Mono.java:4576) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:103) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.MonoNext$NextSubscriber.onError(MonoNext.java:93) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.MonoFlatMapMany$FlatMapManyMain.onError(MonoFlatMapMany.java:205) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.SerializedSubscriber.onError(SerializedSubscriber.java:124) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber.whenError(FluxRetryWhen.java:229) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxRetryWhen$RetryWhenOtherSubscriber.onError(FluxRetryWhen.java:279) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onError(FluxContextWrite.java:121) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.maybeOnError(FluxConcatMapNoPrefetch.java:327) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.onNext(FluxConcatMapNoPrefetch.java:212) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onNext(FluxContextWrite.java:107) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.SinkManyEmitterProcessor.drain(SinkManyEmitterProcessor.java:476) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.SinkManyEmitterProcessor$EmitterInner.drainParent(SinkManyEmitterProcessor.java:620) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxPublish$PubSubInner.request(FluxPublish.java:874) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.request(FluxConcatMapNoPrefetch.java:337) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.Operators$DeferredSubscription.request(Operators.java:1743) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber.onError(FluxRetryWhen.java:196) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.netty.http.client.HttpClientConnect$MonoHttpConnect$ClientTransportSubscriber.onError(HttpClientConnect.java:311) ~[reactor-netty-http-1.1.22.jar:1.1.22]
		at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.netty.resources.DefaultPooledConnectionProvider$DisposableAcquire.onError(DefaultPooledConnectionProvider.java:172) ~[reactor-netty-core-1.1.22.jar:1.1.22]
		at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onError(FluxContextWrite.java:121) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.netty.internal.shaded.reactor.pool.AbstractPool$Borrower.fail(AbstractPool.java:480) ~[reactor-netty-core-1.1.22.jar:1.1.22]
		at reactor.netty.internal.shaded.reactor.pool.SimpleDequePool.lambda$drainLoop$9(SimpleDequePool.java:436) ~[reactor-netty-core-1.1.22.jar:1.1.22]
		at reactor.core.publisher.FluxDoOnEach$DoOnEachSubscriber.onError(FluxDoOnEach.java:186) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.netty.resources.DefaultPooledConnectionProvider$PooledConnectionAllocator$PooledConnectionInitializer.onError(DefaultPooledConnectionProvider.java:583) ~[reactor-netty-core-1.1.22.jar:1.1.22]
		at reactor.core.publisher.MonoFlatMap$FlatMapMain.secondError(MonoFlatMap.java:241) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.MonoFlatMap$FlatMapInner.onError(MonoFlatMap.java:315) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:106) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.Operators.error(Operators.java:198) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.MonoError.subscribe(MonoError.java:53) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.Mono.subscribe(Mono.java:4576) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:103) ~[reactor-core-3.6.9.jar:3.6.9]
		at reactor.netty.transport.TransportConnector$MonoChannelPromise.tryFailure(TransportConnector.java:576) ~[reactor-netty-core-1.1.22.jar:1.1.22]
		at reactor.netty.transport.TransportConnector.lambda$doResolveAndConnect$11(TransportConnector.java:375) ~[reactor-netty-core-1.1.22.jar:1.1.22]
		at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:590) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListenersNow(DefaultPromise.java:557) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListeners(DefaultPromise.java:492) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setValue0(DefaultPromise.java:636) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setFailure0(DefaultPromise.java:629) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setFailure(DefaultPromise.java:110) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.InetSocketAddressResolver$2.operationComplete(InetSocketAddressResolver.java:86) ~[netty-resolver-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:590) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListeners0(DefaultPromise.java:583) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListenersNow(DefaultPromise.java:559) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListeners(DefaultPromise.java:492) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setValue0(DefaultPromise.java:636) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setFailure0(DefaultPromise.java:629) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.tryFailure(DefaultPromise.java:118) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsResolveContext.finishResolve(DnsResolveContext.java:1159) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsResolveContext.tryToFinishResolve(DnsResolveContext.java:1098) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsResolveContext.query(DnsResolveContext.java:457) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsResolveContext.onResponse(DnsResolveContext.java:688) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsResolveContext.access$500(DnsResolveContext.java:69) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsResolveContext$2.operationComplete(DnsResolveContext.java:515) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:590) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListeners0(DefaultPromise.java:583) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListenersNow(DefaultPromise.java:559) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.notifyListeners(DefaultPromise.java:492) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setValue0(DefaultPromise.java:636) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.setSuccess0(DefaultPromise.java:625) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.DefaultPromise.trySuccess(DefaultPromise.java:105) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsQueryContext.trySuccess(DnsQueryContext.java:345) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsQueryContext.finishSuccess(DnsQueryContext.java:336) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.resolver.dns.DnsNameResolver$DnsResponseHandler.channelRead(DnsNameResolver.java:1401) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103) ~[netty-codec-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1407) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:918) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.nio.AbstractNioMessageChannel$NioMessageUnsafe.read(AbstractNioMessageChannel.java:97) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:994) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
		at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Caused by: java.net.UnknownHostException: Failed to resolve 'pokeap.co' [A(1)] after 2 queries 
	at io.netty.resolver.dns.DnsResolveContext.finishResolve(DnsResolveContext.java:1151) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsResolveContext.tryToFinishResolve(DnsResolveContext.java:1098) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsResolveContext.query(DnsResolveContext.java:457) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsResolveContext.onResponse(DnsResolveContext.java:688) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsResolveContext.access$500(DnsResolveContext.java:69) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsResolveContext$2.operationComplete(DnsResolveContext.java:515) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:590) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.notifyListeners0(DefaultPromise.java:583) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.notifyListenersNow(DefaultPromise.java:559) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.notifyListeners(DefaultPromise.java:492) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.setValue0(DefaultPromise.java:636) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.setSuccess0(DefaultPromise.java:625) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.DefaultPromise.trySuccess(DefaultPromise.java:105) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsQueryContext.trySuccess(DnsQueryContext.java:345) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsQueryContext.finishSuccess(DnsQueryContext.java:336) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.resolver.dns.DnsNameResolver$DnsResponseHandler.channelRead(DnsNameResolver.java:1401) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103) ~[netty-codec-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1407) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:918) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.nio.AbstractNioMessageChannel$NioMessageUnsafe.read(AbstractNioMessageChannel.java:97) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562) ~[netty-transport-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:994) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30) ~[netty-common-4.1.112.Final.jar:4.1.112.Final]
	at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Caused by: io.netty.resolver.dns.DnsErrorCauseException: Query failed with NXDOMAIN
	at io.netty.resolver.dns.DnsResolveContext.onResponse(..)(Unknown Source) ~[netty-resolver-dns-4.1.112.Final.jar:4.1.112.Final]

2024-09-17T14:21:26.564-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event ERROR published: 2024-09-17T14:21:26.564452-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded an error: 'org.springframework.web.reactive.function.client.WebClientRequestException: Failed to resolve 'pokeap.co' [A(1)] after 2 queries '. Elapsed time: 25 ms
2024-09-17T14:21:26.564-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event FAILURE_RATE_EXCEEDED published: 2024-09-17T14:21:26.564452-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' exceeded failure rate threshold. Current failure rate: 100.0
2024-09-17T14:21:26.567-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event STATE_TRANSITION published: 2024-09-17T14:21:26.567451200-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' changed state from CLOSED to OPEN
2024-09-17T14:21:26.860-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event NOT_PERMITTED published: 2024-09-17T14:21:26.860452200-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a call which was not permitted.
2024-09-17T14:21:27.207-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event NOT_PERMITTED published: 2024-09-17T14:21:27.207451600-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a call which was not permitted.
2024-09-17T14:21:27.625-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event NOT_PERMITTED published: 2024-09-17T14:21:27.625451400-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a call which was not permitted.
2024-09-17T14:21:28.148-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event NOT_PERMITTED published: 2024-09-17T14:21:28.148468200-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a call which was not permitted.
2024-09-17T14:21:28.610-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event NOT_PERMITTED published: 2024-09-17T14:21:28.610468900-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a call which was not permitted.
2024-09-17T14:21:29.030-05:00 DEBUG 20804 --- [clean_architecture_laboratory] [ctor-http-nio-3] i.g.r.c.i.CircuitBreakerStateMachine     : Event NOT_PERMITTED published: 2024-09-17T14:21:29.030522700-05:00[America/Bogota]: CircuitBreaker 'getAllPokemonsService' recorded a call which was not permitted.

```
Se especifica el metodo fallback para que retorne una respueta en base al fallo
En la url http://localhost:8080/actuator/circuitbreakers se evidencia el estado de los mismos

```java
@CircuitBreaker(name = "getAllPokemonsService", fallbackMethod = "fallbackMethod")
```

```java
    public Flux<String> fallbackMethod(Throwable throwable) {
        return Flux.just("Service is currently unavailable due to: "+ throwable.getMessage());
    }
```

Se observa como empieza a bloquear los eventos y si vamos a revisar el estado con actuator, veremos la cantidad de rechazos y el estado del circuito
```json
{
  "circuitBreakers": {
    "getAllPokemonsService": {
      "failureRate": "100.0%",
      "slowCallRate": "0.0%",
      "failureRateThreshold": "50.0%",
      "slowCallRateThreshold": "50.0%",
      "bufferedCalls": 10,
      "failedCalls": 10,
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "notPermittedCalls": 5,
      "state": "OPEN"
    },
    "getPokemonByUrlService": {
      "failureRate": "-1.0%",
      "slowCallRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "slowCallRateThreshold": "50.0%",
      "bufferedCalls": 0,
      "failedCalls": 0,
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "notPermittedCalls": 0,
      "state": "CLOSED"
    }
  }
}
```


## Implementacion SQS

se implementa un entry point de tipo sqs listener, el cual se configura en local con un local stack, se configura la cola
```yaml
entrypoint:
  sqs:
    region: "us-east-1"
    endpoint: "http://sqs.us-east-1.localhost.localstack.cloud:4566"
    queueUrl: "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/test"
    waitTimeSeconds: 20
    maxNumberOfMessages: 10
    visibilityTimeoutSeconds: 10
    numberOfThreads: 1
```
implementacion de metodo apply para definir logica de procesamiento
Se manda a llamar al mismo caso de uso que usa el otro entrypoint de reactive-web
```java
    @Override
    public Mono<Void> apply(Message message) {
        PokemonQuery query;
        try {
            query = mapper.readValue(message.body(), PokemonQuery.class);
        } catch (JsonProcessingException e) {

            System.out.println(e.getMessage());

            return Mono.empty();
        }
        System.out.println(query.toString());

        useCase.getPokemonByUrl(query.getUrl())
                .doOnNext(pokemon -> System.out.println(pokemon.toString()))
                .doOnError(throwable -> System.out.println("Error al obtener el Pokémon: $throwable"))
                .subscribe();
        return Mono.empty();
    }
```
Probar funcionamiento

crear cola
```bash
awslocal sqs create-queue --queue-name test
```
enviar mensaje con formato definido a la cola
```bash
 awslocal sqs send-message --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/test --message-body '{"url": "world"}'
```

## Redis

Se implementa comunicacion con servidor redis para el almacenamiento de cache en memoria y respuestas dinamicas segun los parametros con menos latencias
Se realiza cambios al servicio original para consultar con los patrones y parametros datos en redis, se realiza un siwtch para consultar nuevos datos y se insertan con una duración de 1 minuto para pruebas

```java
    private final ReactiveRedisTemplate<String, Pokemon> reactiveRedisTemplate;

    private final WebClient client;


    @Override
    @Cacheable("getAllPokemonsService")
    @CircuitBreaker(name = "getAllPokemonsService", fallbackMethod = "fallbackMethod")
    public Flux<Pokemon> getAllPokemons(Integer limit) {

        //var cacheReference = "pokemon:"+limit;
        return reactiveRedisTemplate.keys("pokemon"+limit+":*")
                .flatMap(key -> reactiveRedisTemplate.opsForValue().get(key))
                .switchIfEmpty(client.get()
                        .uri("?limit={limit}", limit)
                        .retrieve()
                        .bodyToMono(PokemonResponse.class)
                        .flatMapMany(response -> Flux.fromIterable(response.getResults())
                                .flatMap(reference -> getPokemonByUrl(reference.getUrl()))))
                .flatMap(pokemon ->
                    reactiveRedisTemplate
                            .opsForValue()
                            .set("pokemon"+limit+":*" + pokemon.getId(), pokemon,  Duration.ofMinutes(1))
                )
                .thenMany(reactiveRedisTemplate
                        .keys("pokemon"+limit+":*")
                        .flatMap(key -> reactiveRedisTemplate.opsForValue().get(key)));
    }
```

prueba con 1000 datos y comparacion de latencias

1000 datos sin persistencia en redis<br>
![image](https://github.com/user-attachments/assets/7a4de058-f8f1-4306-ab55-61c74ab825df)<br>

1000 datos con persistencia en redis<br>
![image](https://github.com/user-attachments/assets/59c5f96c-7cb7-46bb-ac19-f8809c193255)<br>


servidor redis (local)<br>
![image](https://github.com/user-attachments/assets/96a3d7bd-a1fb-48dc-b20e-6b7c33235fd9)<br>
![image](https://github.com/user-attachments/assets/c815796b-bc66-4dbe-af21-93ab0c0e6ac6)<br>




## Arquitectura limpia Bancolombia

Empezaremos por explicar los diferentes componentes del proyectos y partiremos de los componentes externos, continuando con los componentes core de negocio (dominio) y por último el inicio y configuración de la aplicación.

Lee el artículo [Clean Architecture — Aislando los detalles](https://medium.com/bancolombia-tech/clean-architecture-aislando-los-detalles-4f9530f35d7a)

# Arquitectura

![Clean Architecture](https://miro.medium.com/max/1400/1*ZdlHz8B0-qu9Y-QO3AXR_w.png)

## Domain

Es el módulo más interno de la arquitectura, pertenece a la capa del dominio y encapsula la lógica y reglas del negocio mediante modelos y entidades del dominio.

## Usecases

Este módulo gradle perteneciente a la capa del dominio, implementa los casos de uso del sistema, define lógica de aplicación y reacciona a las invocaciones desde el módulo de entry points, orquestando los flujos hacia el módulo de entities.

## Infrastructure

### Helpers

En el apartado de helpers tendremos utilidades generales para los Driven Adapters y Entry Points.

Estas utilidades no están arraigadas a objetos concretos, se realiza el uso de generics para modelar comportamientos
genéricos de los diferentes objetos de persistencia que puedan existir, este tipo de implementaciones se realizan
basadas en el patrón de diseño [Unit of Work y Repository](https://medium.com/@krzychukosobudzki/repository-design-pattern-bc490b256006)

Estas clases no puede existir solas y debe heredarse su compartimiento en los **Driven Adapters**

### Driven Adapters

Los driven adapter representan implementaciones externas a nuestro sistema, como lo son conexiones a servicios rest,
soap, bases de datos, lectura de archivos planos, y en concreto cualquier origen y fuente de datos con la que debamos
interactuar.

### Entry Points

Los entry points representan los puntos de entrada de la aplicación o el inicio de los flujos de negocio.

## Application

Este módulo es el más externo de la arquitectura, es el encargado de ensamblar los distintos módulos, resolver las dependencias y crear los beans de los casos de use (UseCases) de forma automática, inyectando en éstos instancias concretas de las dependencias declaradas. Además inicia la aplicación (es el único módulo del proyecto donde encontraremos la función “public static void main(String[] args)”.

**Los beans de los casos de uso se disponibilizan automaticamente gracias a un '@ComponentScan' ubicado en esta capa.**
