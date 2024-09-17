package com.github.codeby5to.consumer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RestConsumer /* implements Gateway from domain */{
    private final WebClient client;

/*    public PokemonService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://pokeapi.co/api/v2/pokemon").build();
    }*/

    // these methods are an example that illustrates the implementation of WebClient.
    // You should use the methods that you implement from the Gateway from the domain.
    @CircuitBreaker(name = "testGet" /*, fallbackMethod = "testGetOk"*/)
    public Mono<ObjectResponse> testGet() {
        return client
                .get()
                .retrieve()
                .bodyToMono(ObjectResponse.class);
    }

// Possible fallback method
//    public Mono<String> testGetOk(Exception ignored) {
//        return client
//                .get() // TODO: change for another endpoint or destination
//                .retrieve()
//                .bodyToMono(String.class);
//    }

    @CircuitBreaker(name = "testPost")
    public Mono<ObjectResponse> testPost() {
        ObjectRequest request = ObjectRequest.builder()
            .val1("exampleval1")
            .val2("exampleval2")
            .build();
        return client
                .post()
                .body(Mono.just(request), ObjectRequest.class)
                .retrieve()
                .bodyToMono(ObjectResponse.class);
    }
}
