import sys
import os
import subprocess
import tempfile
import re
import asyncio
import edge_tts

def split_sentences(text):
    """Split text into sentences for streaming TTS."""
    parts = re.split(r'(?<=[.!?])\s+', text)
    return [p for p in parts if p.strip()]

def generate_and_play(text, tmpdir):
    """Generate MP3 with edge-tts, convert to WAV, play."""
    base_dir = os.path.dirname(sys.executable) if getattr(sys, "frozen", False) else os.path.dirname(os.path.abspath(__file__))
    package_root = os.path.dirname(base_dir) if getattr(sys, "frozen", False) else base_dir
    ffmpeg = os.path.join(package_root, "runtime", "ffmpeg.exe") if getattr(sys, "frozen", False) else os.path.join(base_dir, "bin", "ffmpeg.exe")
    mp3 = os.path.join(tmpdir, "tts_chunk.mp3")
    wav = os.path.join(tmpdir, "tts_chunk.wav")

    try:
        async def create_audio():
            communicate = edge_tts.Communicate(text, "es-MX-DaliaNeural")
            await communicate.save(mp3)

        asyncio.run(create_audio())
        if not os.path.exists(mp3) or os.path.getsize(mp3) < 500:
            return False

        subprocess.run(
            [ffmpeg, "-y", "-i", mp3, "-acodec", "pcm_s16le",
             "-ar", "22050", "-ac", "1", wav],
            check=True, capture_output=True, timeout=15
        )
        os.remove(mp3)

        if not os.path.exists(wav):
            return False

        import winsound
        # Wait for playback to finish.  Async playback was interrupted when
        # the caller returned (and its duration calculation used 44.1 kHz
        # although the generated WAV is 22.05 kHz).
        winsound.PlaySound(wav, winsound.SND_FILENAME)
        try:
            os.remove(wav)
        except:
            pass
        return True
    except Exception:
        try:
            os.remove(mp3)
        except:
            pass
        return False

def main():
    if len(sys.argv) < 2:
        sys.exit(1)

    mode = sys.argv[1] if len(sys.argv) > 1 else "full"
    text = sys.argv[2] if len(sys.argv) > 2 else ""

    if not text.strip():
        sys.exit(0)

    tmpdir = tempfile.gettempdir()

    if mode == "full":
        # Original: generate all, then play
        generate_and_play(text, tmpdir)

    elif mode == "stream":
        # Stream mode: play first sentence ASAP, then the rest
        sentences = split_sentences(text)
        if not sentences:
            sys.exit(0)

        # Play first sentence immediately
        generate_and_play(sentences[0], tmpdir)

        # Play remaining sentences
        for s in sentences[1:]:
            generate_and_play(s, tmpdir)

    elif mode == "first":
        # Play only the first sentence (for parallel with OpenCode)
        sentences = split_sentences(text)
        if sentences:
            generate_and_play(sentences[0], tmpdir)

if __name__ == "__main__":
    main()
