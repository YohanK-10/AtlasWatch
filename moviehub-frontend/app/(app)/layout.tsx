import { Suspense } from "react";
import { cookies } from "next/headers";
import { AuthProvider } from "@/components/AuthProvider";
import Navbar from "@/components/Navbar";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const cookieStore = await cookies();
  const jwtCookie = cookieStore.get("jwt");
  const isAuthenticated = Boolean(jwtCookie?.value);

  return (
    <AuthProvider isAuthenticated={isAuthenticated}>
      <div className="min-h-screen text-white">
        <Suspense fallback={null}>
          <Navbar />
        </Suspense>
        <main className="pb-16">{children}</main>
      </div>
    </AuthProvider>
  );
}
