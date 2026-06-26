import nodemailer from "nodemailer";

export interface SendResult { delivered: boolean; devCode?: string; }
const DEV = process.env.SA_DEV === "1" || process.env.NODE_ENV !== "production";

export async function sendCode(method: string, identifier: string, code: string): Promise<SendResult> {
  try {
    if (method === "email" && process.env.SMTP_HOST) {
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
    if (method === "phone" && process.env.TWILIO_SID && process.env.TWILIO_TOKEN && process.env.TWILIO_FROM) {
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
    }
  } catch (e) { console.error("sendCode error", e); }
  // Delivery not configured: only reveal code in DEV, never in production.
  if (DEV) { console.log("[DEV OTP] " + identifier + " -> " + code); return { delivered: false, devCode: code }; }
  console.warn("No delivery channel configured for " + method + "; code not sent.");
  return { delivered: false };
}
