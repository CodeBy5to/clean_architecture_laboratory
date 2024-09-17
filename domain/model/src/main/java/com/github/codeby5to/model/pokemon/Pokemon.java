package com.github.codeby5to.model.pokemon;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
//import lombok.NoArgsConstructor;


@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Pokemon {

    private String name;
    private Integer weight;
    private Integer height;
    @JsonProperty("location_area_encounters")
    private String locationAreaEncounters;
    @JsonProperty("base_experience")
    private Integer baseExperience;
    private Integer id;

}
