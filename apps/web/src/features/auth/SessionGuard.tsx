import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { currentSession, type SessionContext } from "./session-api";
import { OnboardingStatusPage } from "../onboarding/OnboardingStatusPage";
import { ApplicationsPage } from "../applications/ApplicationsPage";
import { AuthenticatedLanding } from "./AuthenticatedLanding";
import { PermissionsPage } from "../permissions/PermissionsPage";
import { ApiDocsPage } from "../api-docs/ApiDocsPage";

export function SessionGuard({ expectedPath }: { expectedPath: "/dashboard" | "/onboarding/status" | "/applications" | "/permissions" | "/api-docs" }) {
  const [session, setSession] = useState<SessionContext>();
  const [error, setError] = useState<{ code?: string; message?: string }>();
  useEffect(() => { let active = true; currentSession().then(value => { if (active) setSession(value); })
    .catch(problem => { if (active) setError(problem as { code?: string; message?: string }); });
    return () => { active = false; }; }, []);
  if (error?.code === "AUTHENTICATION_REQUIRED" || error?.code === "INVALID_CREDENTIALS")
    return <Navigate to="/login" replace />;
  if (error) return <main className="registration-shell"><section className="result-card" role="alert"><h1>会话校验未完成</h1><p>{error.message ?? "请稍后重试"}</p></section></main>;
  if (!session) return <main className="registration-shell" aria-busy="true"><section className="result-card"><h1>正在校验会话</h1></section></main>;
  if (session.landingPath !== expectedPath && !(session.onboardingStatus === "APPROVED" && ["/onboarding/status","/applications","/permissions","/api-docs"].includes(expectedPath)))
    return <Navigate to={session.landingPath} replace />;
  if(expectedPath==="/onboarding/status") return <OnboardingStatusPage/>;
  if(expectedPath==="/applications")return <ApplicationsPage/>;
  if(expectedPath==="/permissions")return <PermissionsPage/>;
  return expectedPath==="/api-docs"?<ApiDocsPage/>:<AuthenticatedLanding approved/>;
}
