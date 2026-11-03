#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)/$(uname -m)" != "Linux/x86_64" ]]; then
  echo "This setup script supports Linux x64 only." >&2
  exit 1
fi

directory="$HOME/.local/share/piper"
voice="fr_FR-siwis-medium"
release="https://github.com/rhasspy/piper/releases/download/2023.11.14-2"
voices="https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium"

mkdir -p "$directory/voices"

if [[ ! -x "$directory/piper/piper" ]]; then
  curl -fL "$release/piper_linux_amd64.tar.gz" |
    tar -xz -C "$directory"
fi

for file in "$voice.onnx" "$voice.onnx.json"; do
  if [[ ! -s "$directory/voices/$file" ]]; then
    curl -fL "$voices/$file" -o "$directory/voices/$file.part"
    mv "$directory/voices/$file.part" "$directory/voices/$file"
  fi
done

echo "Piper is ready. Try Listen in the comic reader."
