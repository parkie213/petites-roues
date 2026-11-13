#!/usr/bin/env bash
set -euo pipefail

directory="$HOME/.local/share/piper"
voice="fr_FR-siwis-medium"
voices="https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium"

mkdir -p "$directory/voices"

for file in "$voice.onnx" "$voice.onnx.json"; do
  if [[ ! -s "$directory/voices/$file" ]]; then
    curl -fL "$voices/$file" -o "$directory/voices/$file.part"
    mv "$directory/voices/$file.part" "$directory/voices/$file"
  fi
done

echo "Piper is ready. Try Listen in the comic reader."
