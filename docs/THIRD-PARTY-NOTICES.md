# Bundled speech runtime

The desktop server includes [Piper JNI 1.8.0.0](https://github.com/jvoice-project/piper-jni/releases/tag/v1.8.0.0).
Its bundled native runtime is distributed under GPL-3.0; the Java binding sources are
Apache-2.0. This application's own sources remain under the repository's MIT license.

Piper JNI's corresponding source, build instructions and dependency license information
are available in the [versioned upstream source](https://github.com/jvoice-project/piper-jni/tree/v1.8.0.0)
and its [license](https://github.com/jvoice-project/piper-jni/blob/v1.8.0.0/LICENSE).
The runtime includes Piper, eSpeak NG and ONNX Runtime. Redistributions of the combined
server must preserve the applicable third-party notices and GPL source availability.

Voice models are downloaded separately. Their licenses are specified by their model cards;
the setup script selects [French Siwis medium](https://huggingface.co/rhasspy/piper-voices/blob/main/fr/fr_FR/siwis/medium/MODEL_CARD).
