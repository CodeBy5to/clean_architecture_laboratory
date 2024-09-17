package com.github.codeby5to.consumer.service;

import com.github.codeby5to.consumer.model.PokemonResponse;
import com.github.codeby5to.model.pokemon.Pokemon;
import com.github.codeby5to.model.pokemon.gateways.PokemonRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PokemonService implements PokemonRepository {

    private final WebClient client;


    @Override
    @CircuitBreaker(name = "getAllPokemonsService", fallbackMethod = "fallbackMethod")
    public Flux<Pokemon> getAllPokemons(Integer limit) {
        return Flux.defer(() -> client.get()
                .uri("?limit={limit}", limit)
                .retrieve()
                .bodyToMono(PokemonResponse.class)
                .flatMapMany(response -> Flux.fromIterable(response.getResults())
                        .flatMap(reference -> getPokemonByUrl(reference.getUrl()))));
    }

    @Override
    @CircuitBreaker(name = "getPokemonByUrlService", fallbackMethod = "fallbackMethod2")
    public Mono<Pokemon> getPokemonByUrl(String url) {
        return client.get()
                .uri("/{id}", url.split("/").length > 6 ? url.split("/")[6] : "0")
                .retrieve()
                .bodyToMono(Pokemon.class);
    }

    public Flux<String> fallbackMethod(Throwable throwable) {
        return Flux.just("Service is currently unavailable due to: "+ throwable.getMessage());
    }

    public Mono<String> fallbackMethod2(Throwable throwable) {
        return Mono.just("Service is currently unavailable due to: "+ throwable.getMessage());
    }

}
