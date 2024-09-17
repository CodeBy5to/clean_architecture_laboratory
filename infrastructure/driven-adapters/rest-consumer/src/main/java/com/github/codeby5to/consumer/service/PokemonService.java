package com.github.codeby5to.consumer.service;

import com.github.codeby5to.consumer.model.PokemonResponse;
import com.github.codeby5to.model.pokemon.Pokemon;
import com.github.codeby5to.model.pokemon.gateways.PokemonRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PokemonService implements PokemonRepository {

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
