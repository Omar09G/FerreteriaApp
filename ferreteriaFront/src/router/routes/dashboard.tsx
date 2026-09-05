

import { withFallback } from "@/router/builder";

const DashboardPage = withFallback(() => import("@/features/dashboard/DashboardPage"));

export const dashboardRoutes = [
  {
    path: "dashboard",
    element: <DashboardPage />,
  },
];