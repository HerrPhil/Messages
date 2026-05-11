# The Messages Reference Implementation

Professional development project to galvanize kotlin and
jetpack compose coding skills.

## Purpose

I want to analyze a Gemini (AI) conversation I generated
where the goal is to work up the network-based features
of a Kotlin/Jetpack-Compose app based on AI replies.

I expect gaps. I want to measure how complete the AI suggestions are.

I downloaded the conversation responses.

I want to treat those as a fictional project's
epics, stories. features, and tasks.

## Project Structure

We are starting with a system design of the (default)
single-module structure (Package by Layer)

### data/ (implementation)
This layer is responsible for fetching and saving data.
+ remote/: API interfaces (Retrofit), DTOs (Data Transfer Objects), and Network Data Sources
+ local/: Room Database, DAOs, and Entities
+ repository/: The actual implementation of the repository interfaces defined in the Domain layer
+ mapper/: Functions to convert DTOs/Entities into Domain Models
### domain/ (Business Logic)
This is the "brain" of your app. It should have no dependencies on Android libraries or Data layer.
+ model/: Simple POJOs/Data classes (eg. User, Product)
+ repository/: Interfaces (Abstractions) that the Data layer will implement.
+ use_cases/: Individual classes for specific actions (eg. GetSortedTasksUseCase, LoginUseCase)
### presentation/ (UI Layer)
Everything the user sees and interacts with.
+ components/: Reusable Compose widgets (buttons, cards, headers).
+ screens/: Grouped by feature (eg. permissions/, home/).
  + PermissionsScreen.kt (The Composable UI)
  + PermissionsViewModel.kt (The state holder)
  + PermissionsUiState.kt (A data class, or sealed class, or sealed interface, representing the UI state)