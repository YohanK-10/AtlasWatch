import { Suspense } from "react";
import Navbar from "@/components/Navbar";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen text-white">
      <Suspense fallback={null}>
        <Navbar />
      </Suspense>
      <main className="pb-16">{children}</main>
    </div>
  );
}
