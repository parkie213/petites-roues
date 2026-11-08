import { useEffect, useState } from "react";
import { Button, Code, Group, Modal, Stack, Text, Title } from "@mantine/core";
import QRCode from "qrcode";
import { request, responseError } from "../shared/api.js";
import "./ConnectPhone.css";

const when = (iso) =>
  iso
    ? new Date(iso).toLocaleString(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "never";

/** Seconds left until [expiresAt], never negative. */
const secondsLeft = (expiresAt, now) =>
  Math.max(0, Math.round((Date.parse(expiresAt) - now) / 1000));

/**
 * Pairs a phone with this desktop: a QR code holding the phone listener's addresses and a
 * one-time secret (POST /api/phone/pairings), valid for five minutes, and the phones already
 * paired, which can be forgotten. The phone scans the code from its Desktop dialog. When the phone
 * listener is off, says how to turn it on.
 */
export function ConnectPhone({ opened, onClose }) {
  const [status, setStatus] = useState(null);
  const [pairing, setPairing] = useState(null);
  const [svg, setSvg] = useState("");
  const [phones, setPhones] = useState([]);
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());

  async function newCode() {
    setError("");
    try {
      const started = await request("/api/phone/pairings", { method: "POST" });
      setPairing(started);
      setSvg(
        await QRCode.toString(JSON.stringify(started.code), {
          type: "svg",
          margin: 2,
          errorCorrectionLevel: "M",
        }),
      );
    } catch (failure) {
      setError(failure.message);
    }
  }

  useEffect(() => {
    if (!opened) return;
    let live = true;
    request("/api/phone").then(
      (next) => {
        if (!live) return;
        setStatus(next);
        if (next.enabled) newCode();
      },
      (failure) => live && setError(failure.message),
    );
    // Poll so a phone shows up here as soon as it pairs, and the countdown moves.
    const load = () =>
      request("/api/phones").then(
        (next) => live && setPhones(next),
        () => {},
      );
    load();
    const timer = setInterval(() => {
      setNow(Date.now());
      load();
    }, 2000);
    return () => {
      live = false;
      clearInterval(timer);
      setPairing(null);
      setSvg("");
      setStatus(null);
    };
  }, [opened]);

  async function forget(phone) {
    const response = await fetch(
      `/api/phones/${encodeURIComponent(phone.id)}`,
      { method: "DELETE" },
    );
    if (!response.ok) setError((await responseError(response)).message);
    else setPhones((list) => list.filter((p) => p.id !== phone.id));
  }

  const left = pairing ? secondsLeft(pairing.expiresAt, now) : 0;
  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title="Connect a phone"
      centered
      size="md"
      closeButtonProps={{ "aria-label": "Close dialog" }}
    >
      <Stack gap="md">
        {status && !status.enabled && (
          <Text size="sm">
            Phones can't connect yet: the phone listener is off, because it
            opens a port on your network. Start the server with{" "}
            <Code>--petitesroues.phone.enabled=true</Code>, then open this
            again.
          </Text>
        )}
        {pairing && (
          <>
            <Text size="sm">
              In the petites roues app on your phone, tap the computer icon at
              the top of the library, then <b>Scan QR code</b>.
            </Text>
            <div
              className={`phone-qr${left === 0 ? " phone-qr-expired" : ""}`}
              role="img"
              aria-label="Pairing QR code"
              dangerouslySetInnerHTML={{ __html: svg }}
            />
            <Group justify="space-between">
              <Text size="xs" c="dimmed">
                {left > 0
                  ? `Works once, for ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`
                  : "This code has expired."}
              </Text>
              <Button variant="default" size="xs" fz="xs" onClick={newCode}>
                New code
              </Button>
            </Group>
            <Text size="xs" c="dimmed" style={{ overflowWrap: "anywhere" }}>
              The phone tries {pairing.code.urls.join(", ")}. It must be on the
              same network, and a firewall must allow port {status?.port}.
            </Text>
          </>
        )}
        {error && (
          <Text size="sm" c="red" role="alert">
            {error}
          </Text>
        )}
        <Stack gap={6}>
          <Title order={4} size="sm">
            Paired phones
          </Title>
          {phones.length === 0 && (
            <Text size="sm" c="dimmed">
              None yet.
            </Text>
          )}
          {phones.map((phone) => (
            <Group key={phone.id} justify="space-between" wrap="nowrap">
              <div>
                <Text size="sm" fw={600}>
                  {phone.name}
                </Text>
                <Text size="xs" c="dimmed">
                  Paired {when(phone.pairedAt)} · last seen{" "}
                  {when(phone.lastSeenAt)}
                </Text>
              </div>
              <Button
                variant="subtle"
                color="red"
                size="compact-xs"
                fz="xs"
                onClick={() => forget(phone)}
              >
                Forget
              </Button>
            </Group>
          ))}
        </Stack>
      </Stack>
    </Modal>
  );
}
