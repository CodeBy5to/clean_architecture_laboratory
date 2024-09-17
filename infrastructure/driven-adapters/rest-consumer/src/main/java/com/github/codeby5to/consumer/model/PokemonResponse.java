package com.github.codeby5to.consumer.model;

import lombok.Data;

import java.util.List;

@Data
public class PokemonResponse {

    private Integer count;
    private String next;
    private String previous;
    private List<PokemonReference> results;

}
