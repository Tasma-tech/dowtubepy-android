import os

import yt_dlp


def _build_opts(quality, output_dir, ffmpeg_path, listener, task_id):
    final_ext = None

    if quality == "audio":
        fmt = "bestaudio[ext=m4a]/bestaudio/best"
        if ffmpeg_path:
            final_ext = "mp3"
    elif ffmpeg_path:
        fmt = (
            f"bestvideo[height<={quality}][vcodec^=avc1]"
            f"+bestaudio[acodec^=mp4a]"
            f"/bestvideo[height<={quality}][vcodec^=avc1]+bestaudio"
            f"/bestvideo[height<={quality}]+bestaudio"
            f"/best[height<={quality}]"
        )
        final_ext = "mp4"
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
    if final_ext == "mp3":
        opts["postprocessors"] = [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }
        ]

    return opts, final_ext


def _progress_hook(listener, task_id):
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


def _final_path_hook(store):
    def hook(d):
        if d.get("status") != "finished":
            return
        path = (d.get("info_dict") or {}).get("filepath")
        if path:
            store["path"] = path

    return hook


def _resolve_file_path(ydl, info, hooked_path, final_ext):
    candidates = []

    if hooked_path:
        candidates.append(hooked_path)
    if info.get("filepath"):
        candidates.append(info["filepath"])

    if final_ext:
        adjusted = dict(info)
        adjusted["ext"] = final_ext
        candidates.append(ydl.prepare_filename(adjusted))
    else:
        candidates.append(ydl.prepare_filename(info))

    for path in candidates:
        if path and os.path.exists(path):
            return path

    return candidates[0]


def download(task_id, url, quality, output_dir, ffmpeg_path, listener):
    try:
        opts, final_ext = _build_opts(
            quality, output_dir, ffmpeg_path, listener, task_id
        )

        hooked = {}
        opts["postprocessor_hooks"] = [_final_path_hook(hooked)]

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)

        file_path = _resolve_file_path(ydl, info, hooked.get("path"), final_ext)
        listener.onComplete(task_id, file_path)
        return file_path

    except Exception as exc:
        listener.onError(task_id, str(exc))
        return None
