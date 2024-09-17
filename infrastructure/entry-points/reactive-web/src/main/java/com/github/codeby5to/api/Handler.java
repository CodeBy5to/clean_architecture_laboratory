package com.github.codeby5to.api;

import com.github.codeby5to.model.pokemon.Pokemon;
import com.github.codeby5to.usecase.pokemon.PokemonUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class Handler {
    private  final PokemonUseCase useCase;

    public Mono<ServerResponse> getAllPokemonUseCase(ServerRequest serverRequest) {
        return ServerResponse.ok().body(useCase.getAllPokemon(Integer.parseInt(serverRequest
                .queryParam("limit").orElse("20"))), Pokemon.class);
    }
}
