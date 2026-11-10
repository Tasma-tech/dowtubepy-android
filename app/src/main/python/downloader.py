"""
Módulo Python chamado pelo Kotlin via Chaquopy.

Ponte de comunicação:
    Kotlin (DownloadEngine.submit)  --->  download(...)   [esta função]
    Kotlin (DownloadListener)       <---  listener.onProgress/onComplete/onError

A função `download` é executada na thread "python-bridge" criada em
DownloadEngine.kt — nunca na UI thread. O GIL do Python serializa o
código; um download por vez é processado.
"""

import os

import yt_dlp


def _build_opts(quality, output_dir, ffmpeg_path, listener, task_id):
    """Monta as opções do yt_dlp, espelhando o app desktop (downloader.py).

    Nota sobre o YouTube (2025+): a extração sem runtime de JavaScript não
    disponibiliza nenhum formato *combinado* (vídeo+áudio no mesmo fluxo),
    apenas fluxos DASH separados. Por isso o formato principal é sempre
    ``bestvideo+bestaudio`` (exige FFmpeg para mesclar) — e não ``best``.
    """
    if quality == "audio":
        fmt = "bestaudio/best"
    elif ffmpeg_path:
        fmt = (
            f"bestvideo[height<={quality}][vcodec^=avc1]"
            f"+bestaudio[acodec^=mp4a]"
            f"/bestvideo[height<={quality}][vcodec^=avc1]+bestaudio"
            f"/bestvideo[height<={quality}]+bestaudio"
            f"/best[height<={quality}]"
        )
    else:
        fmt = f"best[height<={quality}]/best"

    opts = {
        "format": fmt,
        "outtmpl": os.path.join(output_dir, "%(title)s.%(ext)s"),
        "progress_hooks": [_progress_hook(listener, task_id)],
        "noplaylist": True,
        "merge_output_format": "mp4",
        "quiet": True,
        "no_warnings": True,
    }

    if ffmpeg_path:
        opts["ffmpeg_location"] = ffmpeg_path

    return opts


def _progress_hook(listener, task_id):
    """Cria o hook de progresso que notifica o Kotlin (thread-safe)."""

    def hook(d):
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            if total > 0:
                percent = d.get("downloaded_bytes", 0) / total * 100
                info = d.get("info_dict") or {}
                title = info.get("title") or ""
                listener.onProgress(task_id, float(percent), title)
        elif d.get("status") == "finished":
            info = d.get("info_dict") or {}
            listener.onProgress(task_id, 100.0, info.get("title") or "")

    return hook


def download(task_id, url, quality, output_dir, ffmpeg_path, listener):
    """
    Executa o download de `url` e reporta o estado via `listener`.

    Args:
        task_id:    identificador gerado no Kotlin, ecoado nos callbacks;
        url:        URL do vídeo;
        quality:    "360" | "720" | "1080" | "audio";
        output_dir: diretório absoluto de saída (app-specific storage);
        ffmpeg_path: caminho absoluto do FFmpeg ou None;
        listener:   objeto Java (DownloadListener) com os callbacks.

    Returns:
        caminho do arquivo baixado, ou None em caso de erro.
    """
    try:
        opts = _build_opts(quality, output_dir, ffmpeg_path, listener, task_id)

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)

        file_path = info.get("filepath") or ydl.prepare_filename(info)
        listener.onComplete(task_id, file_path)
        return file_path

    except Exception as exc:
        listener.onError(task_id, str(exc))
        return None
