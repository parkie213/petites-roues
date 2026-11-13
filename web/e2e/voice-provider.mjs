import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { homedir } from "node:os";
import path from "node:path";

/** A deterministic provider using a real Siwis model, so installation and native preview are real. */
export async function startVoiceProvider(folder) {
  const voices =
    process.env.PETITESROUES_TEST_VOICES ||
    path.join(homedir(), ".local/share/piper/voices");
  const model = path.join(voices, "fr_FR-siwis-medium.onnx");
  const config = model + ".json";
  const id = "fr_FR-comparison-medium";
  const prefix = "fr/fr_FR/comparison/medium/";
  const card = path.join(folder, "MODEL_CARD");
  await writeFile(
    card,
    "Test fixture using the separately installed French Siwis voice. Source: https://huggingface.co/rhasspy/piper-voices\n",
  );
  const sample = path.resolve(
    import.meta.dirname,
    "../../java/desktop-test-fixtures/src/main/resources/media/voice-sample.mp3",
  );
  const files = new Map([
    [prefix + id + ".onnx", model],
    [prefix + id + ".onnx.json", config],
    [prefix + "MODEL_CARD", card],
  ]);
  const metadata = {};
  for (const [name, file] of files) {
    let bytes;
    try {
      bytes = await readFile(file);
    } catch {
      throw Error(
        "Voice journey recording needs the French model: run bash setup/speech.sh first.",
      );
    }
    metadata[name] = {
      size_bytes: bytes.length,
      md5_digest: createHash("md5").update(bytes).digest("hex"),
    };
  }
  const catalogue = JSON.stringify({
    [id]: {
      key: id,
      name: "comparison",
      language: { family: "fr", code: "fr_FR" },
      quality: "medium",
      num_speakers: 1,
      files: metadata,
    },
  });
  files.set(prefix + "speaker_0.mp3", sample);
  let offline = false;
  const server = createServer((request, response) => {
    const name = new URL(request.url, "http://provider").pathname.slice(1);
    if (name === "outage" || name === "restore") {
      offline = name === "outage";
      response.writeHead(204).end();
      return;
    }
    if (name === "voices.json" && offline)
      return response.writeHead(503).end("Provider unavailable");
    if (name === "voices.json") {
      response
        .writeHead(200, { "Content-Type": "application/json" })
        .end(catalogue);
      return;
    }
    const file = files.get(name);
    if (!file) return response.writeHead(404).end();
    response.writeHead(200);
    createReadStream(file).pipe(response);
  });
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(18879, "127.0.0.1", resolve);
  });
  return {
    url: `http://127.0.0.1:${server.address().port}/`,
    close: () =>
      new Promise((resolve) => {
        server.close(resolve);
        server.closeAllConnections();
      }),
  };
}
