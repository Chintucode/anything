import { createBrowserRouter, RouterProvider } from 'react-router'

import { AppShell } from './components/AppShell'
import { NewPlanScreen } from './screens/NewPlanScreen'
import { PlansScreen } from './screens/PlansScreen'
import { TodayScreen } from './screens/TodayScreen'

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <TodayScreen /> },
      { path: 'plans', element: <PlansScreen /> },
      { path: 'plans/new', element: <NewPlanScreen /> },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
