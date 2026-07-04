import nodemailer from "nodemailer";

export interface SendResult { delivered: boolean; devCode?: string; }

// Hard-disable the devCode fallback (operators who require real delivery only).
const DISABLE_DEV_CODE = process.env.DISABLE_DEV_CODE === "1";

function emailConfigured(): boolean { return !!process.env.SMTP_HOST; }
function phoneConfigured(): boolean {
  return !!(process.env.TWILIO_SID && process.env.TWILIO_TOKEN && process.env.TWILIO_FROM);
}

let warnedNoDelivery = false;

export async function sendCode(method: string, identifier: string, code: string): Promise<SendResult> {
  const configured = method === "phone" ? phoneConfigured() : emailConfigured();
  if (configured) {
    try {
      if (method === "email") {
        const transport = nodemailer.createTransport({
          host: process.env.SMTP_HOST,
          port: Number(process.env.SMTP_PORT || 587),
          secure: process.env.SMTP_SECURE === "true",
          auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS },
        });
        await transport.sendMail({
          from: process.env.SMTP_FROM || process.env.SMTP_USER,
          to: identifier,
          subject: "Your SAssist code",
          text: "Your SAssist verification code is " + code,
          html: "<h2>SAssist</h2><p>Your code is <b>" + code + "</b></p>",
        });
        return { delivered: true };
      }
      const sid = process.env.TWILIO_SID as string;
      const basic = Buffer.from(sid + ":" + process.env.TWILIO_TOKEN).toString("base64");
      const params = new URLSearchParams({ To: identifier, From: process.env.TWILIO_FROM as string, Body: "Your SAssist code is " + code });
      const f: any = (globalThis as any).fetch;
      const resp = await f("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json", {
        method: "POST",
        headers: { Authorization: "Basic " + basic, "Content-Type": "application/x-www-form-urlencoded" },
        body: params.toString(),
      });
      if (resp.ok) return { delivered: true };
      console.error("Twilio send failed: HTTP " + resp.status);
    } catch (e) { console.error("sendCode error", e); }
    // A configured channel that fails is a misconfiguration -- never leak the
    // code as a fallback, or a typo in SMTP creds silently weakens auth.
    return { delivered: false };
  }
  // No delivery channel configured for this method. So the server works out of
  // the box, hand the code back to the client (the app shows it on the code
  // screen). Setting SMTP_*/TWILIO_* automatically turns this off.
  if (DISABLE_DEV_CODE) {
    console.warn("No " + method + " delivery configured and DISABLE_DEV_CODE=1; code not sent.");
    return { delivered: false };
  }
  if (!warnedNoDelivery) {
    warnedNoDelivery = true;
    console.warn("[SAssist] No " + method + " delivery configured; returning codes to clients. Configure SMTP_*/TWILIO_* for real delivery, or set DISABLE_DEV_CODE=1 to hard-fail.");
  }
  return { delivered: false, devCode: code };
}
