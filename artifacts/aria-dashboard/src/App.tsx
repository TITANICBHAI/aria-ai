import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Router as WouterRouter, Route, Switch } from "wouter";
import Dashboard from "@/pages/Dashboard";
import NotFound from "@/pages/not-found";
import { Toaster } from "@/components/ui/toaster";

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, "")}>
        <Switch>
          <Route path="/" component={Dashboard} />
          <Route component={NotFound} />
        </Switch>
      </WouterRouter>
      <Toaster />
    </QueryClientProvider>
  );
}
