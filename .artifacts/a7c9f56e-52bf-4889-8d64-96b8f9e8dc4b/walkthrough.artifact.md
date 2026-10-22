# Walkthrough - Fix App Crashes and UI Bugs

## Changes Made

### UI & Fragment Stability
- **[DashboardFragment.kt](file:///D:/WarrantyVault-UI-Redesign/WarrantyVault/app/src/main/java/com/example/warrantyvault/ui/DashboardFragment.kt)**:
  - Switched to `viewLifecycleOwner.lifecycleScope`.
  - Added a null check (`if (_binding == null) return@launch`) after `ProductRepository.getAll()` suspension to prevent `IllegalStateException` / `NullPointerException` when views are destroyed.
- **[ProductListFragment.kt](file:///D:/WarrantyVault-UI-Redesign/WarrantyVault/app/src/main/java/com/example/warrantyvault/ui/ProductListFragment.kt)**:
  - Switched to `viewLifecycleOwner.lifecycleScope`.
  - Added a null check (`if (_binding == null) return@launch`) after `ProductRepository.getAll()` suspension.
  - Implemented `updateFilterSelectionUI()` to dynamically update the background and text color of filter chips (`All`, `Active`, `Expiring Soon`, `Expired`) when selected.

## Verification Results

### Automated Tests
- Ran `gradle_build("app:assembleDebug")`: **SUCCESS** - Build finished successfully without any errors.
