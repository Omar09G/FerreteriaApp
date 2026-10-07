import { withFallback } from "@/router/builder";

const ChatPage = withFallback(() => import("@/features/chat/ChatPage"));

export const chatRoutes = [
	{
		path: "chat",
		element: <ChatPage />,
	},
];
