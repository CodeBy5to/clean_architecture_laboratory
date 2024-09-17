package com.github.codeby5to.api;

import com.github.codeby5to.model.pokemon.Pokemon;
import com.github.codeby5to.usecase.pokemon.PokemonUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@org.springframework.web.bind.annotation.RestController
public class RestController {

    private final PokemonUseCase useCase;

    @GetMapping("/pokemon")
    public Flux<Pokemon> getUser(@RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return useCase.getAllPokemon(limit);
    }
}
