# dowtubepy 
<p align="center">
  <img src="dowtube-py/icon/logo.svg" width="220" alt="Notohiis Logo">
</p>

dowtubepy é um app para baixa videos do youtube.

## Estrutura

- `dowtube-py/` — aplicação desktop original (Python + CustomTkinter + `yt_dlp`).
- `app/` — aplicativo Android nativo em Kotlin (Jetpack Compose / Material 3)
  que embute o Python via **Chaquopy** e reutiliza a lógica do `yt_dlp`.

## Android

### Arquitetura

```
UI (Compose, Material 3)
   │  observe StateFlow<List<DownloadItem>>
   ▼
DownloadViewModel ─── startDownload() ───► DownloadEngine (thread "python-bridge")
   ▲                                              │
   │  DownloadListener.onProgress/Complete/Error  │ Chaquopy (Kotlin → Python)
   └──────────────────────────────────────────────┴──► downloader.py (yt_dlp + FFmpeg)
```

- **UI:** `HomeScreen.kt` — campo de URL, seletor de qualidade, botão
  "Baixar" e lista de downloads com barra de progresso (100% Compose/M3).
- **Ponte Kotlin → Python:** `DownloadEngine.kt` roda o `yt_dlp` em uma
  thread própria, nunca na UI thread.
- **Ponte Python → Kotlin:** `DownloadListener.kt` é chamado pelo
  `progress_hooks` do `yt_dlp` e atualiza os `StateFlow` de forma
  thread-safe.
- **FFmpeg:** `FFmpegResolver.kt` extrai o binário de
  `app/src/main/assets/ffmpeg/` e o repassa ao `yt_dlp` como
  `ffmpeg_location` (necessário para mesclar `bestvideo + bestaudio`).
  Sem FFmpeg, o formato cai para um progressivo sem mesclagem.

### Requisitos de build

- JDK 17+ e Android SDK (compileSdk 36).
- **Python 3.11 no PATH** (versão exigida pelo `yt-dlp` e usada pelo
  Chaquopy para instalar pacotes e compilar bytecode). Ex.:
  ```bash
  uv python install 3.11
  ln -s "$(uv python find 3.11)/bin/python3.11" ~/.local/bin/python3.11
  ```
  Ou aponte o caminho na própria build (sem symlink):
  ```bash
  ./gradlew :app:assembleDebug -Ppython311="$(uv python find 3.11)/bin/python3.11"
  ```
- FFmpeg (opcional): coloque o binário em
  `app/src/main/assets/ffmpeg/<abi>/ffmpeg` — ver
  `app/src/main/assets/ffmpeg/README.md`.

### Build

```bash
./gradlew :app:assembleDebug -Ppython311="$(uv python find 3.11)/bin/python3.11"
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk` (~69 MB,
inclui o Python 3.11, o `yt-dlp` e as dependências para 3 ABIs).
