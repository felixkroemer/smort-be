package com.felixkroemer.smort.infrastructure.dynamodb.deck;

public enum DeckStatus {
    IMPORTING,
    ACTIVE,
    MARKED_FOR_DELETION,
    DELETED
}
