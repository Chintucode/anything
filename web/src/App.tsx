import { createBrowserRouter, RouterProvider } from 'react-router'

import { AppShell } from './components/AppShell'
import { RouteErrorScreen } from './components/ErrorBoundary'
import { NewPlanScreen } from './screens/NewPlanScreen'
import { PlansScreen } from './screens/PlansScreen'
import { TodayScreen } from './screens/TodayScreen'

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      {
        // A pathless route whose only job is to catch. A screen that throws
        // renders the recovery card in the shell's outlet, so the tab bar above
        // it still works and there is always a way out.
        errorElement: <RouteErrorScreen />,
        children: [
          { index: true, element: <TodayScreen /> },
          { path: 'plans', element: <PlansScreen /> },
          { path: 'plans/new', element: <NewPlanScreen /> },
        ],
      },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
