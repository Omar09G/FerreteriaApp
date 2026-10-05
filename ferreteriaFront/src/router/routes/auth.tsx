
import { Navigate } from "react-router-dom";

import { withFallback } from "@/router/builder";
import { RedirigirSiAutenticado } from "@/router/guards";

const Login = withFallback(() => import("@/features/auth/Login"));
const Otp = withFallback(() => import("@/features/auth/Otp"));
const GoogleCallback = withFallback(() => import("@/features/auth/GoogleCallback"));

export const authRoutes = [
  {
    path: "/login",
    element: (
      <RedirigirSiAutenticado>
        <Login />
      </RedirigirSiAutenticado>
    ),
  },
  {
    path: "/auth/otp",
    element: (
      <RedirigirSiAutenticado>
        <Otp />
      </RedirigirSiAutenticado>
    ),
  },
  {
    path: "/auth/callback",
    element: (
      <RedirigirSiAutenticado>
        <GoogleCallback />
      </RedirigirSiAutenticado>
    ),
  },
  { path: "/", element: <Navigate to="/login" replace /> },
];