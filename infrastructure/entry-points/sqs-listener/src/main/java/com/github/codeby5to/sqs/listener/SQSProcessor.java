package com.github.codeby5to.sqs.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.codeby5to.sqs.listener.model.PokemonQuery;
import com.github.codeby5to.usecase.pokemon.PokemonUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SQSProcessor implements Function<Message, Mono<Void>> {
    private final PokemonUseCase useCase;

    private final ObjectMapper mapper =new ObjectMapper();

    @Override
    public Mono<Void> apply(Message message) {
        PokemonQuery query;
        try {
            query = mapper.readValue(message.body(), PokemonQuery.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        System.out.println(query.toString());
        useCase.getPokemonByUrl(query.getUrl()).doOnNext(pokemon -> System.out.println(pokemon.toString()));
        return Mono.empty();
    }
}
