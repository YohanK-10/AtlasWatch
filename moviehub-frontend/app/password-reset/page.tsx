"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  confirmPasswordReset,
  getErrorMessage,
  requestPasswordReset,
} from "@/lib/api";

export default function PasswordResetPage() {
  const [email, setEmail] = useState("");
  const [resetCode, setResetCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [confirmPasswordError, setConfirmPasswordError] = useState("");
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);
  const [error, setError] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [sending, setSending] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const router = useRouter();

  useEffect(() => {
    if (cooldown === 0) return;
    const id = window.setInterval(() => setCooldown((value) => value - 1), 1000);
    return () => window.clearInterval(id);
  }, [cooldown]);

  useEffect(() => {
    setError("");
  }, [email, resetCode, newPassword, confirmPassword]);

  useEffect(() => {
    setConfirmPasswordError(validateConfirmPassword(newPassword, confirmPassword));
  }, [newPassword, confirmPassword]);

  const checkPassword = (password: string): string => {
    if (!password) return "";

    if (
      password.length < 8 ||
      !/[A-Z]/.test(password) ||
      !/[a-z]/.test(password) ||
      !/\d/.test(password) ||
      !/[!@#$%^&*()]/.test(password)
    ) {
      return "Password must be at least 8 characters and contain at least one uppercase and lowercase letter, one digit and one special character";
    }

    return "";
  };

  const validateConfirmPassword = (password: string, confirmation: string): string => {
    if (!password || !confirmation) return "";
    return password === confirmation ? "" : "The passwords you entered do not match";
  };

  const handleSendCode = async (event: React.FormEvent) => {
    event.preventDefault();
    setSending(true);
    setMessage(null);
    setError("");

    try {
      await requestPasswordReset(email);
      setCodeSent(true);
      setCooldown(30);
      setMessage({
        text: "If an account exists for that email, a password reset code has been sent.",
        ok: true,
      });
    } catch (err) {
      setError(getErrorMessage(err, "We couldn't start the password reset flow."));
    } finally {
      setSending(false);
    }
  };

  const handleResetPassword = async (event: React.FormEvent) => {
    event.preventDefault();
    const nextPasswordError = checkPassword(newPassword);
    const nextConfirmPasswordError = validateConfirmPassword(newPassword, confirmPassword);

    setPasswordError(nextPasswordError);
    setConfirmPasswordError(nextConfirmPasswordError);

    if (nextPasswordError || nextConfirmPasswordError) {
      return;
    }

    setResetting(true);
    setMessage(null);
    setError("");

    try {
      await confirmPasswordReset(email, resetCode, newPassword);
      setMessage({ text: "Password reset successfully. You can log in with your new password now.", ok: true });
      window.setTimeout(() => router.push("/login"), 1200);
    } catch (err) {
      setError(getErrorMessage(err, "We couldn't reset your password."));
    } finally {
      setResetting(false);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white">
      <div className="mx-auto flex min-h-screen w-full max-w-6xl items-center justify-center px-4 py-12">
        <div className="grid w-full overflow-hidden rounded-[2rem] border border-slate-800/70 bg-slate-950/88 shadow-[0_24px_90px_rgba(0,0,0,0.45)] lg:grid-cols-[0.92fr,1.08fr]">
          <section className="relative hidden overflow-hidden bg-[radial-gradient(circle_at_top_left,rgba(245,158,11,0.24),transparent_28%),linear-gradient(160deg,#140d04_0%,#0b0d14_48%,#030406_100%)] p-10 lg:flex lg:flex-col lg:justify-between">
            <div>
              <p className="text-xs uppercase tracking-[0.32em] text-amber-300/85">Account recovery</p>
              <h1 className="mt-5 text-4xl font-semibold leading-tight text-white">
                Get back into AtlasWatch without starting over.
              </h1>
            </div>

            <div className="space-y-4 rounded-[1.5rem] border border-white/10 bg-white/5 p-6 backdrop-blur-md">
              <p className="text-sm uppercase tracking-[0.24em] text-slate-400">How it works</p>
              <div className="space-y-3 text-sm leading-7 text-slate-200">
                <p>Enter the email tied to your account and we’ll send a six-digit reset code.</p>
                <p>Use that code here to set a fresh password, then head straight back to login.</p>
              </div>
            </div>
          </section>

          <section className="p-6 sm:p-8 lg:p-10">
            <div className="mx-auto max-w-xl space-y-8">
              <div className="space-y-3">
                <p className="text-xs uppercase tracking-[0.3em] text-amber-300/85">Password reset</p>
                <h2 className="text-3xl font-semibold text-white">Reset your password</h2>
                <p className="text-sm leading-7 text-slate-400">
                  Keep browsing public pages if you want, but you’ll need a fresh sign-in to review films or update your watchlist.
                </p>
              </div>

              <form onSubmit={handleSendCode} className="space-y-5 rounded-[1.5rem] border border-slate-800/65 bg-white/[0.03] p-5 sm:p-6">
                <div>
                  <label htmlFor="reset-email" className="mb-2 block text-sm font-medium text-slate-300">
                    Email address
                  </label>
                  <input
                    id="reset-email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="you@example.com"
                    className="field-input"
                    required
                  />
                </div>

                <button
                  type="submit"
                  disabled={sending || !email.trim() || cooldown > 0}
                  className="btn-primary"
                >
                  {cooldown > 0 ? `Resend in ${cooldown}s` : sending ? "Sending code..." : codeSent ? "Resend code" : "Send reset code"}
                </button>
              </form>

              <form onSubmit={handleResetPassword} className="space-y-5 rounded-[1.5rem] border border-slate-800/65 bg-white/[0.03] p-5 sm:p-6">
                <div className="space-y-1">
                  <h3 className="text-lg font-semibold text-white">Enter your reset code</h3>
                  <p className="text-sm text-slate-400">
                    Once the email arrives, paste the code here and choose your new password.
                  </p>
                </div>

                <div>
                  <label htmlFor="reset-code" className="mb-2 block text-sm font-medium text-slate-300">
                    Reset code
                  </label>
                  <input
                    id="reset-code"
                    type="text"
                    value={resetCode}
                    onChange={(event) => setResetCode(event.target.value)}
                    placeholder="6-digit code"
                    className="field-input tracking-[0.3em]"
                    required
                  />
                </div>

                <div>
                  <label htmlFor="new-password" className="mb-2 block text-sm font-medium text-slate-300">
                    New password
                  </label>
                  <input
                    id="new-password"
                    type="password"
                    value={newPassword}
                    onChange={(event) => {
                      const nextPassword = event.target.value;
                      setNewPassword(nextPassword);
                      setPasswordError(checkPassword(nextPassword));
                    }}
                    onBlur={() => setPasswordError(checkPassword(newPassword))}
                    placeholder="Choose a stronger password"
                    className={`field-input ${passwordError ? "border-rose-500 focus:border-rose-500" : ""}`}
                    required
                  />
                  {passwordError ? (
                    <p className="mt-2 text-xs text-rose-300">{passwordError}</p>
                  ) : (
                    <p className="mt-2 text-xs text-slate-500">
                      Use at least 8 characters with uppercase, lowercase, a number, and a symbol.
                    </p>
                  )}
                </div>

                <div>
                  <label htmlFor="confirm-password" className="mb-2 block text-sm font-medium text-slate-300">
                    Confirm new password
                  </label>
                  <input
                    id="confirm-password"
                    type="password"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    onBlur={() => setConfirmPasswordError(validateConfirmPassword(newPassword, confirmPassword))}
                    placeholder="Type the same password again"
                    className={`field-input ${confirmPasswordError ? "border-rose-500 focus:border-rose-500" : ""}`}
                    required
                  />
                  {confirmPasswordError ? (
                    <p className="mt-2 text-xs text-rose-300">{confirmPasswordError}</p>
                  ) : (
                    <p className="mt-2 text-xs text-slate-500">
                      Re-enter your new password so you can confirm exactly what you typed.
                    </p>
                  )}
                </div>

                <button
                  type="submit"
                  disabled={
                    resetting ||
                    !email.trim() ||
                    !resetCode.trim() ||
                    !newPassword.trim() ||
                    !confirmPassword.trim() ||
                    Boolean(passwordError) ||
                    Boolean(confirmPasswordError)
                  }
                  className="btn-primary"
                >
                  {resetting ? "Resetting..." : "Reset password"}
                </button>
              </form>

              {message && (
                <p className={`text-sm ${message.ok ? "text-emerald-400" : "text-rose-300"}`}>
                  {message.text}
                </p>
              )}
              {error && <p className="text-sm text-rose-300">{error}</p>}

              <div className="flex items-center gap-3 text-sm text-slate-400">
                <Link href="/login" className="text-slate-300 transition hover:text-white">
                  Back to login
                </Link>
                <span className="text-slate-700">/</span>
                <Link href="/register" className="text-slate-300 transition hover:text-white">
                  Create account
                </Link>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
