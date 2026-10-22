# Implementation Plan - Fix App Crashes and UI Bugs

## Problem Statement
The user reported that the app has a bug and is crashing. Upon analyzing the codebase, a primary crash vector is identified: ViewBinding reference access (`_binding!!`) in fragments (`DashboardFragment` and `ProductListFragment`) after coroutines resume from asynchronous calls (`ProductRepository.getAll()`) when the fragment view has already been destroyed (`onDestroyView()`). Additionally, filter chips in `ProductListFragment` do not update their visual selection state when clicked.

## Proposed Changes

### UI Components

#### [MODIFY] [DashboardFragment.kt](file:///D:/WarrantyVault-UI-Redesign/WarrantyVault/app/src/main/java/com/example/warrantyvault/ui/DashboardFragment.kt)
- Switch from `lifecycleScope` to `viewLifecycleOwner.lifecycleScope`.
- Add a null check (`if (_binding == null) return@launch`) after `ProductRepository.getAll()` suspension to prevent `IllegalStateException` / `NullPointerException` when views are destroyed.

#### [MODIFY] [ProductListFragment.kt](file:///D:/WarrantyVault-UI-Redesign/WarrantyVault/app/src/main/java/com/example/warrantyvault/ui/ProductListFragment.kt)
- Switch from `lifecycleScope` to `viewLifecycleOwner.lifecycleScope`.
- Add a null check (`if (_binding == null) return@launch`) after `ProductRepository.getAll()` suspension.
- Implement helper method to update filter chip selection backgrounds/styles visually when a filter is clicked.

## Verification Plan

### Automated Tests
- Run `gradle_build("app:assembleDebug")` to verify successful compilation and packaging.

### Manual Verification
- Deploy and test navigating between Dashboard and Products tabs while background operations complete, and test clicking different filter chips.
