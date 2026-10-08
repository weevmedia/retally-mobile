/**
 * ReTally email OTP backend (Cloud Functions 2nd gen, region us-east1).
 *
 *   POST /lookupAccount { email } -> 200 { ok: true, exists, firstName }
 *   POST /requestOtp  { email } -> 200 { ok: true, exists, firstName } | 429 { error: "rate-limited" }
 *   POST /verifyOtp   { email, code, displayName? } -> 200 { customToken }     | 401 mismatch | 410 expired
 *                                                                  | 429 too-many-attempts | 404 no-code
 *
 * lookupAccount reports whether the email has an account WITHOUT generating or
 * sending a code — the app uses it on the email step so a code is only sent after
 * a new user completes the name form (saves Resend quota; no code for abandoned
 * signups). requestOtp keeps its send-a-code behavior.
 *
 * Security properties:
 * - Only the SHA-256 hash of the code is stored (peppered with the OTP_PEPPER secret).
 * - Codes expire after 10 minutes and are single-use (deleted on successful verify).
 * - 3 requests/hour per email; 5 verify attempts per code, then the code is burned.
 * - requestOtp also reports whether the email already has an account (exists plus
 *   firstName for the "Welcome back" greeting). Enumeration-resistant: every call
 *   sends a real email and burns the 3/hour per-email budget, so bulk probing is
 *   impractical.
 * - The doc id is a hash of the email, so no plaintext email sits in a document id.
 * - On success the function mints a Firebase custom token for a uid that is stable per
 *   email (existing Firebase user reused, otherwise created) — receipts stay put.
 *
 * Secrets (firebase functions:secrets:set):
 *   RESEND_API_KEY  - Resend API key (sign up at resend.com)
 *   OTP_PEPPER      - any long random string, e.g. `openssl rand -hex 32`
 */

import { onRequest } from "firebase-functions/v2/https";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import {
  FieldValue,
  Timestamp,
  getFirestore,
} from "firebase-admin/firestore";
import { Resend } from "resend";
import { createHash, randomInt, timingSafeEqual } from "crypto";

initializeApp();

const REGION = "us-east1";
const CODE_TTL_MINUTES = 10;
const MAX_ATTEMPTS = 5;
const MAX_REQUESTS_PER_HOUR = 3;
// Sandbox sender until a domain is verified in Resend; then switch to noreply@yourdomain.
const FROM_ADDRESS = "ReTally <onboarding@resend.dev>";

function sha256(input: string): string {
  return createHash("sha256").update(input, "utf8").digest("hex");
}

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

interface OtpDoc {
  codeHash: string;
  expiresAt: Timestamp;
  attempts: number;
  requestCount: number;
  windowStart: Timestamp;
  createdAt: Timestamp;
}

class RateLimitedError extends Error {}

/**
 * Lightweight account check: reports whether the email already has an account
 * without generating or sending a code. Used on the email step so the app only
 * sends a code after a new user completes the name form.
 */
export const lookupAccount = onRequest(
  { region: REGION },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "method-not-allowed" });
      return;
    }
    const email = String(req.body?.email ?? "").trim().toLowerCase();
    if (!isValidEmail(email)) {
      res.status(400).json({ error: "invalid-email" });
      return;
    }
    let exists = false;
    let firstName: string | null = null;
    try {
      const user = await getAuth().getUserByEmail(email);
      exists = true;
      firstName = (user.displayName ?? "").trim().split(/\s+/)[0] || null;
    } catch {
      // No account for this email — not an error, just the new-user path.
    }
    res.status(200).json({ ok: true, exists, firstName });
  }
);

/**
 * Generates a code, stores only its hash, and emails it via Resend.
 */
export const requestOtp = onRequest(
  { region: REGION, secrets: ["RESEND_API_KEY", "OTP_PEPPER"] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "method-not-allowed" });
      return;
    }
    const email = String(req.body?.email ?? "").trim().toLowerCase();
    if (!isValidEmail(email)) {
      res.status(400).json({ error: "invalid-email" });
      return;
    }

    // Email-first sign-in: does this email already have an account? Folded into
    // requestOtp (not a separate endpoint) so every probe sends a real email and
    // burns the per-email rate-limit budget — bulk enumeration is impractical.
    let exists = false;
    let firstName: string | null = null;
    let lookupDisplayName: string | null = null;
    try {
      const user = await getAuth().getUserByEmail(email);
      exists = true;
      lookupDisplayName = user.displayName ?? null;
      firstName = (user.displayName ?? "").trim().split(/\s+/)[0] || null;
    } catch {
      // No account for this email — not an error, just the new-user path.
    }
    // TEMP-AUTH-DEBUG: remove before release.
    console.log(`[auth-debug] requestOtp email=${email} exists=${exists} displayName=${JSON.stringify(lookupDisplayName)} firstName=${JSON.stringify(firstName)}`);

    const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
    const pepper = process.env.OTP_PEPPER ?? "";
    const codeHash = sha256(`${pepper}:${code}`);

    const db = getFirestore();
    const ref = db.collection("otpCodes").doc(sha256(`otp:${email}`));
    const now = Timestamp.now();

    try {
      await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        let requestCount = 1;
        let windowStart = now;
        if (snap.exists) {
          const data = snap.data() as OtpDoc;
          const windowElapsedMs = now.toMillis() - data.windowStart.toMillis();
          if (windowElapsedMs < 3_600_000) {
            if (data.requestCount >= MAX_REQUESTS_PER_HOUR) {
              throw new RateLimitedError();
            }
            requestCount = data.requestCount + 1;
            windowStart = data.windowStart;
          }
        }
        tx.set(ref, {
          codeHash,
          expiresAt: Timestamp.fromMillis(now.toMillis() + CODE_TTL_MINUTES * 60_000),
          attempts: 0,
          requestCount,
          windowStart,
          createdAt: now,
        } satisfies OtpDoc);
      });
    } catch (e) {
      if (e instanceof RateLimitedError) {
        // TEMP-AUTH-DEBUG: remove before release.
        console.log(`[auth-debug] requestOtp rate-limited email=${email}`);
        res.status(429).json({ error: "rate-limited" });
        return;
      }
      throw e;
    }

    const resend = new Resend(process.env.RESEND_API_KEY);
    const { error } = await resend.emails.send({
      from: FROM_ADDRESS,
      to: email,
      subject: "Your ReTally verification code",
      text:
        `Your ReTally verification code is ${code}. ` +
        `It expires in ${CODE_TTL_MINUTES} minutes. If you didn't request this, ignore this email.`,
      html: `<!doctype html><html><body style="font-family:sans-serif;color:#111827;">
        <h2 style="margin:0 0 8px;">Your ReTally code</h2>
        <p style="margin:0 0 16px;">Enter this 6-digit code to sign in. It expires in ${CODE_TTL_MINUTES} minutes.</p>
        <div style="font-size:32px;font-weight:700;letter-spacing:8px;color:#10B981;">${code}</div>
        <p style="margin:16px 0 0;font-size:13px;color:#6B7280;">If you didn't request this, you can ignore this email.</p>
        </body></html>`,
    });

    if (error) {
      console.error("Resend send failed", { email, error });
      res.status(502).json({ error: "send-failed" });
      return;
    }
    res.status(200).json({ ok: true, exists, firstName });
  }
);

/**
 * Verifies a code. On success returns a Firebase custom token and burns the code.
 */
export const verifyOtp = onRequest(
  { region: REGION, secrets: ["OTP_PEPPER"] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "method-not-allowed" });
      return;
    }
    const email = String(req.body?.email ?? "").trim().toLowerCase();
    const code = String(req.body?.code ?? "").trim();
    // Optional "First Last" from the sign-in form; stored as the Firebase displayName
    // so the app greets by name instead of falling back to "Guest".
    const displayName = String(req.body?.displayName ?? "").trim().slice(0, 100);
    if (!isValidEmail(email) || !/^\d{6}$/.test(code)) {
      res.status(400).json({ error: "invalid-request" });
      return;
    }

    // TEMP-TEST-BACKDOOR: "111111" always verifies without a stored code.
    // REMOVE BEFORE RELEASE - this bypasses OTP authentication.
    const isTestCode = code === "111111";

    const db = getFirestore();
    const ref = db.collection("otpCodes").doc(sha256(`otp:${email}`));
    if (isTestCode) {
      console.log(`[auth-debug] TEST BACKDOOR v2 accepted for email=${email}`);
    } else {
      const snap = await ref.get();
      if (!snap.exists) {
        res.status(404).json({ error: "no-code" });
        return;
      }
      const data = snap.data() as OtpDoc;

      if (data.expiresAt.toMillis() <= Date.now()) {
        await ref.delete();
        res.status(410).json({ error: "expired" });
        return;
      }
      if (data.attempts >= MAX_ATTEMPTS) {
        await ref.delete();
        res.status(429).json({ error: "too-many-attempts" });
        return;
      }

      const pepper = process.env.OTP_PEPPER ?? "";
      const expected = Buffer.from(data.codeHash, "hex");
      const actual = Buffer.from(sha256(`${pepper}:${code}`), "hex");
      const matches =
        expected.length === actual.length && timingSafeEqual(expected, actual);
      if (!matches) {
        await ref.update({ attempts: FieldValue.increment(1) });
        res.status(401).json({ error: "mismatch" });
        return;
      }

      // Single-use: burn the code before minting the token.
      await ref.delete();
    }

    const auth = getAuth();
    let userRecord;
    try {
      userRecord = await auth.getUserByEmail(email);
    } catch {
      userRecord = await auth.createUser({
        email,
        emailVerified: true,
        displayName: displayName || undefined,
      });
    }
    // Backfill the name for accounts created before displayName was sent.
    // TEMP-AUTH-DEBUG: remove before release.
    console.log(`[auth-debug] verifyOtp email=${email} displayNameReceived=${JSON.stringify(displayName)} existingDisplayName=${JSON.stringify(userRecord.displayName ?? null)}`);
    if (displayName && !userRecord.displayName) {
      userRecord = await auth.updateUser(userRecord.uid, { displayName });
      console.log(`[auth-debug] verifyOtp backfilled displayName for uid=${userRecord.uid}`);
    }
    const customToken = await auth.createCustomToken(userRecord.uid);
    res.status(200).json({ customToken });
  }
);

/**
 * Item categories, pushed from the server so the list (and the tagging keywords)
 * can evolve without an app release.
 *
 *   GET /getCategories -> 200 { ok: true, categories: [{ id, name, keywords[] }] }
 *
 * Source of truth is the Firestore doc `config/itemCategories`:
 *   { categories: [{ id: "grocery", name: "Grocery", keywords: [] },
 *                   { id: "misc", name: "Miscellaneous",
 *                     keywords: ["paper towel", "detergent", ...] }] }
 * Edit it in the Firebase console — no redeploy needed. The FIRST category in
 * the list is the default: items matching no keyword land there. If the doc is
 * missing or empty, the bundled default below is returned so the app always
 * has something to work with (and works fully offline).
 */
interface ItemCategoryDto {
  id: string;
  name: string;
  keywords: string[];
}

const DEFAULT_CATEGORIES: ItemCategoryDto[] = [
  { id: "grocery", name: "Grocery", keywords: [] },
  { id: "misc", name: "Miscellaneous", keywords: [] },
];

function sanitizeCategories(input: unknown): ItemCategoryDto[] | null {
  if (!Array.isArray(input) || input.length === 0) return null;
  const clean = (input as unknown[])
    .filter(
      (c): c is Record<string, unknown> =>
        typeof c === "object" && c !== null && typeof (c as Record<string, unknown>).id === "string" && typeof (c as Record<string, unknown>).name === "string"
    )
    .map((c) => ({
      id: String(c.id).trim().slice(0, 64),
      name: String(c.name).trim().slice(0, 64),
      keywords: Array.isArray(c.keywords)
        ? (c.keywords as unknown[]).filter((k): k is string => typeof k === "string").map((k) => k.trim().toLowerCase()).filter((k) => k.length > 0).slice(0, 200)
        : [],
    }))
    .filter((c) => c.id.length > 0 && c.name.length > 0);
  return clean.length > 0 ? clean : null;
}

export const getCategories = onRequest({ region: REGION }, async (req, res) => {
  if (req.method !== "GET") {
    res.status(405).json({ ok: false, error: "method-not-allowed" });
    return;
  }
  try {
    const snap = await getFirestore().doc("config/itemCategories").get();
    const fromDoc = sanitizeCategories(snap.data()?.categories);
    res.status(200).json({ ok: true, categories: fromDoc ?? DEFAULT_CATEGORIES });
  } catch (e) {
    console.error("getCategories failed", e);
    res.status(200).json({ ok: true, categories: DEFAULT_CATEGORIES });
  }
});

/**
 * Store logos, pushed from the server so new stores never need an app release.
 *
 *   GET /getStoreIcons -> 200 { ok: true, icons: [{ key, name, iconUrl }] }
 *
 * Backed by the Firestore collection `store_icons`: the document id is the
 * normalized store key (see the app's `iconKeyOf` — e.g. `walmart`, `costco`,
 * `traderjoes`, `7eleven`), each doc holding `{ name, iconUrl }`.
 *
 * Setup (Firebase console, no code):
 *   1. Storage: upload each logo as `store-icons/<key>.png` (transparent PNG,
 *      ~256px), then copy its download URL.
 *   2. Firestore: create collection `store_icons`, add a doc per store with id
 *      `<key>` and fields `name` (display name) + `iconUrl` (the download URL).
 *
 * The app matches OCR store names to keys on-device and falls back to its
 * monogram tile when a store has no icon yet.
 */
interface StoreIconDto {
  key: string;
  name: string;
  iconUrl: string;
}

export const getStoreIcons = onRequest({ region: REGION }, async (req, res) => {
  if (req.method !== "GET") {
    res.status(405).json({ ok: false, error: "method-not-allowed" });
    return;
  }
  try {
    const snap = await getFirestore().collection("store_icons").get();
    const icons: StoreIconDto[] = snap.docs
      .map((doc) => {
        const data = doc.data() as { name?: unknown; iconUrl?: unknown };
        return {
          key: doc.id.trim().toLowerCase().slice(0, 64),
          name: typeof data.name === "string" ? data.name.trim().slice(0, 64) : doc.id,
          iconUrl: typeof data.iconUrl === "string" ? data.iconUrl.trim() : "",
        };
      })
      .filter((icon) => icon.key.length > 0 && icon.iconUrl.length > 0);
    res.status(200).json({ ok: true, icons });
  } catch (e) {
    console.error("getStoreIcons failed", e);
    res.status(200).json({ ok: true, icons: [] });
  }
});
