# Relatives

A native Android person browser for the FamilySearch take-home exercise.

## Development status

The project is being built in small, independently verified changes. The planned sequence is Android setup, typed records and persistence, the people list, profiles and relative navigation, then offline verification and final documentation.

## Approach

Kotlin and Jetpack Compose suit the native Android role. One application module and manual dependency injection keep the two-screen application small and explainable. The planned data flow is Retrofit → repository → Room → Flow → ViewModel StateFlow → Compose. Room will hold the last successfully saved records so refresh failures do not discard previously loaded data.

Run instructions, dependencies, verification results, tradeoffs, known gaps, and actual time spent will be updated as each feature is completed.
