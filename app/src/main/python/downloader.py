import os
import re
import urllib.request

import yt_dlp

TEXT_ENCODING = 1


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


def _http_bytes(url, timeout=25):
    request = urllib.request.Request(
        url, headers={"User-Agent": "Mozilla/5.0 (Linux; Android 14)"}
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def _pick_thumbnail(info):
    for thumb in reversed(info.get("thumbnails") or []):
        url = thumb.get("url") or ""
        if url.split("?")[0].lower().endswith(".jpg"):
            return url, "image/jpeg"
    for thumb in reversed(info.get("thumbnails") or []):
        url = thumb.get("url") or ""
        lower = url.split("?")[0].lower()
        if lower.endswith(".webp"):
            return url, "image/webp"
        if lower.endswith(".png"):
            return url, "image/png"
    single = info.get("thumbnail")
    return (single, "image/jpeg") if single else (None, None)


def _pick_subtitle(info):
    languages = ("pt-BR", "pt", "en-US", "en")
    for key in ("subtitles", "automatic_captions"):
        table = info.get(key) or {}
        for wanted in languages:
            for lang, formats in table.items():
                if not lang.lower().startswith(wanted.lower()):
                    continue
                for entry in formats:
                    url = entry.get("url")
                    if url and entry.get("ext") in ("vtt", "srt"):
                        return lang, url
    return None, None


def _subtitle_to_text(data):
    try:
        raw = data.decode("utf-8")
    except UnicodeDecodeError:
        raw = data.decode("latin-1", "ignore")

    lines = []
    previous = None
    for line in raw.splitlines():
        line = line.strip()
        if (
            not line
            or "-->" in line
            or line.startswith(("WEBVTT", "Kind:", "Language:", "NOTE"))
            or line.isdigit()
        ):
            continue
        line = re.sub(r"<[^>]+>", "", line).replace("&nbsp;", " ").strip()
        if not line or line == previous:
            continue
        lines.append(line)
        previous = line
    return "\n".join(lines)


def _text_frame(frame_cls, value):
    return frame_cls(encoding=TEXT_ENCODING, text=value)


def _tag_mp3(path, info, cover, cover_mime, lyrics, lang):
    from mutagen.id3 import APIC, ID3, ID3NoHeaderError, TALB, TCON, TDRC, TIT2, TRCK, TPE1, USLT

    try:
        tags = ID3(path)
    except ID3NoHeaderError:
        tags = ID3()

    title = info.get("title")
    artist = (
        info.get("artist")
        or info.get("creator")
        or info.get("uploader")
        or info.get("channel")
    )
    album = info.get("album")
    date = info.get("release_year") or (info.get("upload_date") or "")[:4] or None
    genre = info.get("genre")
    track = info.get("track_number")

    frames = []
    if title:
        frames.append(_text_frame(TIT2, str(title)))
    if artist:
        frames.append(_text_frame(TPE1, str(artist)))
    if album:
        frames.append(_text_frame(TALB, str(album)))
    if date:
        frames.append(_text_frame(TDRC, str(date)))
    if genre:
        frames.append(_text_frame(TCON, str(genre)))
    if track:
        frames.append(_text_frame(TRCK, str(track)))
    if cover:
        frames.append(
            APIC(
                encoding=TEXT_ENCODING,
                mime=cover_mime or "image/jpeg",
                type=3,
                desc="Cover",
                data=cover,
            )
        )
    if lyrics:
        frames.append(
            USLT(encoding=TEXT_ENCODING, lang=lang or "por", desc="", text=lyrics)
        )

    for frame in frames:
        tags.delall(type(frame).__name__)
        tags.add(frame)

    tags.save(path, v2_version=3)


def _tag_mp4(path, info, cover, cover_mime, lyrics, lang):
    from mutagen.mp4 import MP4, MP4Cover

    tags = MP4(path)
    title = info.get("title")
    artist = (
        info.get("artist")
        or info.get("creator")
        or info.get("uploader")
        or info.get("channel")
    )
    date = info.get("release_year") or (info.get("upload_date") or "")[:4] or None
    genre = info.get("genre")

    if title:
        tags["\xa9nam"] = [str(title)]
    if artist:
        tags["\xa9ART"] = [str(artist)]
    if info.get("album"):
        tags["\xa9alb"] = [str(info["album"])]
    if date:
        tags["\xa9day"] = [str(date)]
    if genre:
        tags["\xa9gen"] = [str(genre)]
    if cover:
        fmt = MP4Cover.FORMAT_JPEG
        if cover_mime == "image/png":
            fmt = MP4Cover.FORMAT_PNG
        tags["covr"] = [MP4Cover(cover, imageformat=fmt)]
    if lyrics:
        tags["\xa9lyr"] = [lyrics]

    tags.save()


def apply_audio_metadata(path, info):
    if not path or not os.path.exists(path):
        return

    cover = None
    cover_mime = None
    lyrics = None
    lang = None

    thumb_url, thumb_mime = _pick_thumbnail(info)
    if thumb_url:
        try:
            cover = _http_bytes(thumb_url)
            cover_mime = thumb_mime
        except Exception:
            cover = None

    sub_lang, sub_url = _pick_subtitle(info)
    if sub_url:
        try:
            lyrics = _subtitle_to_text(_http_bytes(sub_url))
            lang = "por" if sub_lang.lower().startswith("pt") else "eng"
        except Exception:
            lyrics = None

    lower = path.lower()
    if lower.endswith(".mp3"):
        _tag_mp3(path, info, cover, cover_mime, lyrics, lang)
    elif lower.endswith((".m4a", ".mp4")):
        _tag_mp4(path, info, cover, cover_mime, lyrics, lang)


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

        if quality == "audio":
            try:
                apply_audio_metadata(file_path, info)
            except Exception:
                pass

        listener.onComplete(task_id, file_path)
        return file_path

    except Exception as exc:
        listener.onError(task_id, str(exc))
        return None
