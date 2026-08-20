import { RegistrationPage } from "../features/onboarding/RegistrationPage";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "../features/auth/LoginPage";
import { SessionGuard } from "../features/auth/SessionGuard";

export function App() {
  return (
    <BrowserRouter><div className="shell">
      <header className="masthead"><span className="brand-mark" aria-hidden="true">OP</span><span>轻量化开放平台</span></header>
      <Routes><Route path="/" element={<Navigate to="/register" replace />} /><Route path="/register" element={<RegistrationPage />} />
        <Route path="/login" element={<LoginPage />} /><Route path="/onboarding/status" element={<SessionGuard expectedPath="/onboarding/status" />} />
        <Route path="/dashboard" element={<SessionGuard expectedPath="/dashboard" />} /><Route path="/applications" element={<SessionGuard expectedPath="/applications" />} /><Route path="/permissions" element={<SessionGuard expectedPath="/permissions" />} /><Route path="/api-docs" element={<SessionGuard expectedPath="/api-docs" />} /><Route path="*" element={<main><h1>页面不存在</h1></main>} /></Routes>
    </div></BrowserRouter>
  );
}
