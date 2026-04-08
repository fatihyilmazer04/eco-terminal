package com.ecoterminal.model.dto;

public record PreferencesRequest(
        String seatPreference,
        String mealPreference,
        Boolean crowdAlerts,
        Boolean flightUpdates,
        Boolean routeSuggestions,
        Boolean ecoRewards
) {}
