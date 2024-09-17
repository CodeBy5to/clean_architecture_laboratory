package com.github.codeby5to.sqs.listener.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
public class PokemonQuery {
    private String url;
}
