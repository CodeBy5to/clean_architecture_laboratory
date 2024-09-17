package com.github.codeby5to.config;

import com.github.codeby5to.model.pokemon.gateways.PokemonRepository;
import com.github.codeby5to.usecase.pokemon.PokemonUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(basePackages = "com.github.codeby5to.usecase",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
        },
        useDefaultFilters = false)
public class UseCasesConfig {

        @Bean
        public PokemonUseCase pokemonUseCase(final PokemonRepository pokemonRepository){
                return new PokemonUseCase(pokemonRepository);
        }
}
