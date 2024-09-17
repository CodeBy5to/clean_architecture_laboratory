# Clean Architecture Laboratory
Proyecto Springboot con reactor y netty usando el plugin clean architecture de Bancolombia, implementando un adaptador de tipo webclient para consultar datos de la pokeApi agregando el patron circuitbreaker y fallBack. Adicional se implementan 2 entry points uno para exponer los servicios en rest y otro para crear un listener SQS


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

```java
@CircuitBreaker(name = "getAllPokemonsService", fallbackMethod = "fallbackMethod")
```

```java
    public Flux<String> fallbackMethod(Throwable throwable) {
        return Flux.just("Service is currently unavailable due to: "+ throwable.getMessage());
    }
```
Se especifica el metodo fallback para que retorne una respueta en base al fallo
En la url http://localhost:8080/actuator/circuitbreakers se evidencia el estado de los mismos

```json
{
  "circuitBreakers": {
    "getAllPokemonsService": {
      "failureRate": "-1.0%",
      "slowCallRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "slowCallRateThreshold": "50.0%",
      "bufferedCalls": 3,
      "failedCalls": 0,
      "slowCalls": 1,
      "slowFailedCalls": 0,
      "notPermittedCalls": 0,
      "state": "CLOSED"
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
