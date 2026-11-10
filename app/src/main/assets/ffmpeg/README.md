# FFmpeg para Android — agora via `jniLibs` (não mais `assets`)

O `yt_dlp` precisa do **FFmpeg** para mesclar os fluxos separados de vídeo e
áudio (`bestvideo + bestaudio`). Sem ele, downloads de vídeo falham no
YouTube atual, que não oferece mais formatos combinados sem runtime JS.

## Por que `jniLibs` e não `assets`

Desde o Android 10 (API 29) a política **W^X** do SELinux proíbe executar
qualquer arquivo gravado em diretório gravável pelo app — mesmo com
`chmod +x`. Isso invalidou a abordagem antiga (extrair de `assets/` para
`filesDir` e dar `exec`), que resultava em:

```
error=13, Permission denied
```

A única rota viável é o **diretório de bibliotecas nativas**
(`applicationInfo.nativeLibraryDir`), que o sistema extrai do APK na
instalação, marca como somente-leitura e permite `exec`.

## Estrutura atual

```
app/src/main/jniLibs/
├── arm64-v8a/libffmpeg.so    <- binário FFmpeg renomeado (dispositivos 64-bit)
├── armeabi-v7a/libffmpeg.so  <- binário FFmpeg renomeado (32-bit)
└── x86_64/libffmpeg.so       <- binário FFmpeg renomeado (emuladores)
```

O PackageManager **não valida o conteúdo** dos `.so`, apenas o sufixo —
por isso um executável ELF pode ser empacotado assim e extraído com
permissão de execução.

`FFmpegResolver.kt` localiza o arquivo em `nativeLibraryDir` e repassa o
caminho ao yt-dlp como `ffmpeg_location`.

## Origem dos binários

| ABI           | FFmpeg | Fonte |
|---------------|--------|-------|
| arm64-v8a     | 9.0    | https://github.com/hzw1199/Android-FFmpeg-Prebuilt |
| armeabi-v7a   | 3.3.2  | https://github.com/Khang-NT/ffmpeg-binary-android |
| x86_64        | 3.3.2  | https://github.com/Khang-NT/ffmpeg-binary-android |

Os binários são LGPL/GPL conforme a licença de cada build de origem —
verifique a compatibilidade antes de distribuir publicamente.

## Comportamento sem FFmpeg

Se nenhum binário for encontrado, `FFmpegResolver.resolve()` retorna
`null` e o `downloader.py` cai para um formato progressivo único (que
não precisa de mesclagem). No YouTube esse formato pode não existir, de
modo que **empacotar o FFmpeg é essencial para vídeos**.
