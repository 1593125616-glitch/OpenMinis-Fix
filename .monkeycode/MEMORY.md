# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[User Instruction Summary]
- Date: 2026-09-16
- Context: 用户明确指示思考模式使用中文，并写入记忆
- Instructions:
  - 所有思考过程（internal reasoning / scratchpad / analysis）必须使用中文书写。
  - 输出给用户的内容仍按用户语言规则执行（本会话为中文）。

[Project Knowledge Summary]
- Date: 2026-09-16
- Context: Discovered by Agent while attempting to compile/validate the Android patches in this repo
- Category: Environment Configuration
- Instructions:
  - This workspace has no `java`/JDK, no `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and no Android SDK/NDK installed; `./gradlew` cannot be executed here.
  - Kotlin/Swift changes in this repo must be validated by static review only in this environment; they are NOT compiled or unit-tested here.
  - Source layout: Android app under `src/android/`, iOS app under `src/ios/`. `deps/proot` is a git submodule (native build needed for a real APK).
