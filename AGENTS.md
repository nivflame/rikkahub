# Repository Guidelines

本文档面向贡献者，概述本仓库的模块结构、开发流程，便于快速上手并保持一致的协作质量。

## Build, Test, and Development Commands

使用 Android Studio 或命令行 Gradle：

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew connectedDebugAndroidTest  # 运行设备/模拟器上的仪器测试
./gradlew lint                   # 运行 Android Lint
```

构建应用需要在 `app/` 下提供 `google-services.json`（用于 Firebase）。
`web` 模块会在 `preBuild` 阶段构建 `web-ui/` 并复制静态资源，需要本地可用 `pnpm`。

## Coding Style & Naming Conventions

本仓库使用 `.editorconfig` 统一格式：

- Kotlin/Gradle 脚本：4 空格缩进，最大行长 120。
- XML/JSON：2 空格缩进。
- Markdown/YAML：2 空格缩进，允许尾随空格（用于对齐）。

命名习惯：模块名为小写目录（如 `ai/`、`speech/`），Kotlin 类遵循 PascalCase，测试类以 `*Test` 结尾。

## Icons

本项目使用 [HugeIcons](https://hugeicons.com/) 的 Jetpack Compose 封装库（`com.github.rikkahub:hugeicons-compose`，定义在 `gradle/libs.versions.toml` 中）。

### 挑选图标

1. 在 [hugeicons.com/icons](https://hugeicons.com/icons) 上搜索和浏览免费 Stroke Rounded 风格的图标
2. 找到所需图标后，将它的 kebab-case 名称转换为 PascalCase（例如 `shield-key` 变成 `ShieldKey`）
3. 通过验证 GitHub 上库的源文件来确认图标是否存在：
   `https://raw.githubusercontent.com/rikkahub/hugeicons-compose/main/library/src/main/java/me/rerere/hugeicons/stroke/<PascalCaseName>.kt`
4. 在 Kotlin 文件中导入并使用：
   ```kotlin
   import me.rerere.hugeicons.stroke.ShieldKey
   // ...
   Icon(HugeIcons.ShieldKey, null)
   ```

### 注意事项

- 同一页面或设置列表中的不同条目应使用不同的图标，避免视觉混淆
- 不要将元工具（如 ToolSearch）列在需要审批的工具列表中，也不要将 ToolSearch 本身列为可延迟（deferred）的工具

## Testing Guidelines

测试框架以 JUnit/AndroidX Test 为主。未设定强制覆盖率门槛，但新逻辑应配套新增/更新测试。测试文件命名建议：

- 单元测试：`FooTest.kt`
- 仪器测试：`FooInstrumentedTest.kt` 或 `*Test.kt`

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Fork Maintenance

This is a fork of `rikkahub/rikkahub`. Upstream remote is `origin`, fork remote is `fork`.

When syncing with upstream:
1. `git fetch origin` to get latest upstream
2. `git merge origin/master --no-edit` to bring in new commits
3. Resolve conflicts: keep fork tools (browser, subagent, ToolSearch), exclude ScreenTimeTool, CalendarTool, TimeInfoTool, and TextToSpeechTool (intentionally deleted)
4. If squashing: `git reset --soft origin/master` (NEVER reset to old merge-base, causes "commits behind" on GitHub)
5. Create backup branch before squash: `git branch -f backup-before-squash-N HEAD`
6. Force push is acceptable for this personal fork

NEVER add fork-specific Room database migrations. Fork features use DataStore to preserve upstream database compatibility.

## Commit Squashing

Squash incomplete feature commits to keep history clean. Only squash commits that are part of the same incomplete feature (e.g. a feature commit followed by fixup commits that fix build errors or finish missed changes). Do NOT squash bug fixes, specific issue fixes, optimization commits, or independent features: these should remain separate for traceability.

### When to squash

- A feature commit followed by one or more fixup commits that fix build errors, missing imports, or finish incomplete changes in the same feature
- Two commits with the same message where the first was incomplete and the second finished it

### When NOT to squash

- Bug fixes (e.g. "Fix negative comment scores")
- Independent features (e.g. "Add PDF support to WebFetch")
- Optimization or behavior changes (e.g. "Use realistic Chrome user agent")
- Refactors or improvements that stand on their own

### Procedure

1. Create a backup branch: `git branch -f backup-before-squash-N HEAD`
2. Use `GIT_SEQUENCE_EDITOR` to script the rebase since interactive editors are unavailable:
   ```
   GIT_SEQUENCE_EDITOR="cp /path/to/rebase-todo.txt" git rebase -i HEAD~N
   ```
3. Mark fixup commits with `fixup` (discard their message) and keep feature commits with `pick`
4. If commits are non-consecutive, reorder them in the todo file so the fixup commit immediately follows the feature commit
5. Verify with `git log --oneline` after rebase
6. Do NOT push unless explicitly asked

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.
