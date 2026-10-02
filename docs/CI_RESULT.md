# Latest Handover Android CI run

- Run: https://github.com/khaledhasneen1993/Handover/actions/runs/37048102766
- Source commit: 852d67c76b076e5fbb9cd0242bb2f3ef3e021db7
- Gradle outcome: success
- Gradle exit code: 1
- Runner: ubuntu-latest, Temurin 17, Gradle 9.3.1, Android API 36

This is automated runner evidence, not a device/camera test or release certification.

## Selected diagnostics
```text
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:105:29 Unresolved reference 'isNullOrEmpty'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:137:43 Unresolved reference 'isNullOrEmpty'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:182:28 Unresolved reference 'keySet'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:216:41 Unresolved reference 'keySet'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:217:71 Unresolved reference 'it'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:219:64 Unresolved reference 'it'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/media/AssetStore.kt:75:5 Missing return statement.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:21:16 Type 'MutableState<String>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:21:16 Type 'MutableState<String>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:22:22 Type 'MutableState<String>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:22:22 Type 'MutableState<String>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:23:15 Type 'MutableState<String>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:23:15 Type 'MutableState<String>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:24:16 Type 'MutableState<String>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:24:16 Type 'MutableState<String>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:25:17 Type 'MutableState<String>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:25:17 Type 'MutableState<String>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:26:17 Type 'MutableIntState' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:26:17 Type 'MutableIntState' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:27:17 Type 'MutableState<Boolean>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:27:17 Type 'MutableState<Boolean>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:28:15 Type 'MutableState<String?>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:28:15 Type 'MutableState<String?>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:29:20 Type 'MutableState<File?>' has no method 'getValue(HandoverViewModel, KMutableProperty1<*, *>)', so it cannot serve as a delegate.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:29:20 Type 'MutableState<File?>' has no method 'setValue(HandoverViewModel, KMutableProperty1<*, *>, ERROR CLASS: Unresolved name: getValue)', so it cannot serve as a delegate for var (read-write property).
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:31:50 Unresolved reference 'getValue'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/HandoverViewModel.kt:40:36 Unresolved reference 'getValue'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:97:42 Cannot infer type for this parameter. Specify it explicitly.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:97:46 Cannot infer type for this parameter. Specify it explicitly.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:273:108 Condition type mismatch: inferred type is 'WideNavigationRailValue' but 'Boolean' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:430:153 Condition type mismatch: inferred type is 'WideNavigationRailValue' but 'Boolean' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:653:123 Argument type mismatch: actual type is 'WideNavigationRailValue', but 'Boolean' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:658:78 Argument type mismatch: actual type is 'WideNavigationRailValue', but 'Boolean' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:675:90 Argument type mismatch: actual type is 'Any', but 'File' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:691:91 Argument type mismatch: actual type is 'Any', but 'File' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:761:47 Argument type mismatch: actual type is 'WideNavigationRailValue', but 'Boolean' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/ui/Screens.kt:767:72 Condition type mismatch: inferred type is 'WideNavigationRailValue' but 'Boolean' was expected.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:105:29 Unresolved reference 'isNullOrEmpty'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:137:43 Unresolved reference 'isNullOrEmpty'.
e: file:///home/runner/work/Handover/Handover/app/src/main/java/com/khaled/handover/backup/BackupManager.kt:182:28 Unresolved reference 'keySet'.
```
