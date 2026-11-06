package com.example.poker.domain;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ResultCalculator {

    public RoundResult calculate(Collection<Card> votes) {
        Map<Card, Integer> counts = new EnumMap<>(Card.class);
        votes.forEach(card -> counts.merge(card, 1, Integer::sum));

        List<CardCount> groups = counts.entrySet().stream()
                .map(e -> new CardCount(e.getKey(), e.getValue()))
                .toList();

        Double average = votes.stream()
                .filter(Card::isNumeric)
                .mapToInt(Card::points)
                .average()
                .stream().boxed().findFirst().orElse(null);

        Card mostFrequent = groups.stream()
                .max(Comparator.comparingInt(CardCount::count).thenComparing(CardCount::card))
                .map(CardCount::card)
                .orElse(null);

        return new RoundResult(groups, average, mostFrequent);
    }
}
