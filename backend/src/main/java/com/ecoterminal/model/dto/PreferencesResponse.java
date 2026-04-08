package com.ecoterminal.model.dto;

public record PreferencesResponse(
        String seatPreference,
        String mealPreference,
        boolean crowdAlerts,
        boolean flightUpdates,
        boolean routeSuggestions,
        boolean ecoRewards
) {
    public static PreferencesResponse defaults() {
        return new PreferencesResponse("NO_PREFERENCE", "STANDARD", true, true, true, true);
    }
}
