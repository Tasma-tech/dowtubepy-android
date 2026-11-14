import json
import os
import re
import time
import urllib.request

import yt_dlp

TEXT_ENCODING = 1
VIDEO_HEIGHT = {"max": None, "medium": 720, "low": 360}
AUDIO_BITRATE = {"max": "320", "medium": "192", "low": "128"}


def _format_spec(media, quality, has_ffmpeg):
    if media == "audio":
        return "bestaudio[ext=m4a]/bestaudio/best"

    height = VIDEO_HEIGHT.get(quality)

    if not has_ffmpeg:
        return "best" if height is None else f"best[height<={height}]/best"

    if height is None:
        return (
            "bestvideo[vcodec^=avc1]+bestaudio[acodec^=mp4a]"
            "/bestvideo+bestaudio"
            "/best[ext=mp4]/best"
        )

    return (
        f"bestvideo[height<={height}][vcodec^=avc1]+bestaudio[acodec^=mp4a]"
        f"/bestvideo[height<={height}][vcodec^=avc1]+bestaudio"
        f"/bestvideo[height<={height}]+bestaudio"
        f"/best[height<={height}]"
    )


def _final_ext(media, has_ffmpeg):
    if media == "audio":
        return "mp3" if has_ffmpeg else None
    return "mp4" if has_ffmpeg else None


def _build_opts(media, quality, is_playlist, outtmpl, ffmpeg_path, listener, task_id):
    has_ffmpeg = bool(ffmpeg_path)

    opts = {
        "format": _format_spec(media, quality, has_ffmpeg),
        "outtmpl": outtmpl,
        "progress_hooks": [_progress_hook(listener, task_id)],
        "noplaylist": not is_playlist,
        "merge_output_format": "mp4",
        "quiet": True,
        "no_warnings": True,
    }

    if has_ffmpeg:
        opts["ffmpeg_location"] = ffmpeg_path

    if media == "audio" and has_ffmpeg:
        opts["postprocessors"] = [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": AUDIO_BITRATE.get(quality, "192"),
            }
        ]

    return opts


def _probe_playlist(url):
    opts = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": "in_playlist",
        "noplaylist": False,
        "skip_download": True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
    return bool(info and info.get("entries") is not None)


def _overall_percent(info, percent):
    index = info.get("playlist_index")
    total = info.get("n_entries") or info.get("playlist_count") or 0
    if index and total and total > 1:
        return ((index - 1) + percent / 100.0) / total * 100.0
    return percent


def _display_title(info):
    if info.get("playlist_index") and info.get("playlist_title"):
        return info.get("playlist_title")
    return info.get("title") or ""


def _progress_hook(listener, task_id):
    def hook(d):
        info = d.get("info_dict") or {}
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            if total <= 0:
                return
            percent = d.get("downloaded_bytes", 0) / total * 100
            listener.onProgress(
                task_id, _overall_percent(info, percent), _display_title(info)
            )
        elif d.get("status") == "finished":
            listener.onProgress(
                task_id, _overall_percent(info, 100.0), _display_title(info)
            )

    return hook


def _remember_info(store, info):
    for key in ("filepath", "_filename", "filename"):
        candidate = info.get(key)
        if not candidate:
            continue
        stem = os.path.splitext(os.path.basename(candidate))[0]
        store["infos"].setdefault(stem, info)


def _final_path_hook(store):
    def hook(d):
        if d.get("status") != "finished":
            return
        info = d.get("info_dict") or {}
        path = info.get("filepath")
        if path and path not in store["paths"]:
            store["paths"].append(path)
        _remember_info(store, info)

    return hook


def _pick_paths(postprocessed, sources):
    chosen = {}
    order = []
    for path in list(postprocessed) + list(sources):
        if not path or not os.path.exists(path):
            continue
        stem = os.path.splitext(os.path.basename(path))[0]
        if stem in chosen:
            continue
        chosen[stem] = path
        order.append(stem)
    return [chosen[stem] for stem in order]


def _fallback_paths(ydl, info, final_ext):
    if not info or info.get("entries"):
        return []
    candidate = ydl.prepare_filename(info)
    if final_ext:
        candidate = os.path.splitext(candidate)[0] + "." + final_ext
    return [candidate] if candidate and os.path.exists(candidate) else []


def _recent_files(output_dir, since):
    found = []
    for name in sorted(os.listdir(output_dir)):
        path = os.path.join(output_dir, name)
        if not os.path.isfile(path):
            continue
        if os.path.getmtime(path) >= since:
            found.append(path)
    return found


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


def _artist_of(info):
    return (
        info.get("artist")
        or info.get("creator")
        or info.get("uploader")
        or info.get("channel")
    )


def _tag_mp3(path, info, cover, cover_mime, lyrics, lang):
    from mutagen.id3 import APIC, ID3, ID3NoHeaderError, TALB, TCON, TDRC, TIT2, TRCK, TPE1, USLT

    try:
        tags = ID3(path)
    except ID3NoHeaderError:
        tags = ID3()

    artist = _artist_of(info)
    date = info.get("release_year") or (info.get("upload_date") or "")[:4] or None

    frames = []
    if info.get("title"):
        frames.append(_text_frame(TIT2, str(info["title"])))
    if artist:
        frames.append(_text_frame(TPE1, str(artist)))
    if info.get("album"):
        frames.append(_text_frame(TALB, str(info["album"])))
    if date:
        frames.append(_text_frame(TDRC, str(date)))
    if info.get("genre"):
        frames.append(_text_frame(TCON, str(info["genre"])))
    if info.get("track_number"):
        frames.append(_text_frame(TRCK, str(info["track_number"])))
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
    artist = _artist_of(info)
    date = info.get("release_year") or (info.get("upload_date") or "")[:4] or None

    if info.get("title"):
        tags["\xa9nam"] = [str(info["title"])]
    if artist:
        tags["\xa9ART"] = [str(artist)]
    if info.get("album"):
        tags["\xa9alb"] = [str(info["album"])]
    if date:
        tags["\xa9day"] = [str(date)]
    if info.get("genre"):
        tags["\xa9gen"] = [str(info["genre"])]
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


def download(task_id, url, media, quality, playlist, output_dir, ffmpeg_path, listener):
    try:
        started_at = time.time()

        is_playlist = False
        if playlist:
            try:
                is_playlist = _probe_playlist(url)
            except Exception:
                is_playlist = False

        if is_playlist:
            outtmpl = os.path.join(
                output_dir, "%(playlist_title)s", "%(playlist_index)s - %(title)s.%(ext)s"
            )
        else:
            outtmpl = os.path.join(output_dir, "%(title)s.%(ext)s")

        opts = _build_opts(
            media, quality, is_playlist, outtmpl, ffmpeg_path, listener, task_id
        )

        store = {"paths": [], "infos": {}}
        sources = []

        def finished_hook(d):
            if d.get("status") != "finished":
                return
            info = d.get("info_dict") or {}
            path = info.get("filepath") or info.get("_filename")
            if path:
                sources.append(path)
            _remember_info(store, info)

        opts["progress_hooks"].append(finished_hook)
        opts["postprocessor_hooks"] = [_final_path_hook(store)]

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)

            paths = _pick_paths(store["paths"], sources)
            if not paths:
                paths = _fallback_paths(ydl, info, _final_ext(media, bool(ffmpeg_path)))
            if not paths:
                paths = _recent_files(output_dir, started_at)

        if not paths:
            raise RuntimeError("Nenhum arquivo foi gerado.")

        if media == "audio":
            infos = store["infos"]
            fallback = next(iter(infos.values())) if len(infos) == 1 else None
            for path in paths:
                stem = os.path.splitext(os.path.basename(path))[0]
                entry = infos.get(stem) or infos.get(stem.split(" - ", 1)[-1]) or fallback
                if entry:
                    try:
                        apply_audio_metadata(path, entry)
                    except Exception:
                        pass

        listener.onComplete(task_id, json.dumps(paths))
        return paths

    except Exception as exc:
        listener.onError(task_id, str(exc))
        return None
