package com.github.codeby5to.usecase.pokemon;

import com.github.codeby5to.model.pokemon.Pokemon;
import com.github.codeby5to.model.pokemon.gateways.PokemonRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PokemonUseCase {

    private final PokemonRepository pokemonRepository;



    public Flux<Pokemon> getAllPokemon(Integer limit) {
        return pokemonRepository.getAllPokemons(limit);
    }


    public Mono<Pokemon> getPokemonByUrl(String url) {
        return pokemonRepository.getPokemonByUrl(url);
    }


}
