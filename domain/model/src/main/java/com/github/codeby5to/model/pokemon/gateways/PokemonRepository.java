package com.github.codeby5to.model.pokemon.gateways;

import com.github.codeby5to.model.pokemon.Pokemon;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PokemonRepository {

    Flux<Pokemon> getAllPokemons(Integer limit);
    Mono<Pokemon> getPokemonByUrl(String url);

}
