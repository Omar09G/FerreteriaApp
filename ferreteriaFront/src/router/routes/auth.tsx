
import { Navigate } from "react-router-dom";

import { withFallback } from "@/router/builder";
import { RedirigirSiAutenticado } from "@/router/guards";

const Login = withFallback(() => import("@/features/auth/Login"));

export const authRoutes = [
  {
    path: "/login",
    element: (
      <RedirigirSiAutenticado>
        <Login />
      </RedirigirSiAutenticado>
    ),
  },
  { path: "/", element: <Navigate to="/login" replace /> },
];