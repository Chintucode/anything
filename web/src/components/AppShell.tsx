import { Outlet } from 'react-router'

import { TabBar } from './TabBar'

/** Every screen sits inside this: content column + floating tab bar. */
export function AppShell() {
  return (
    <div className="app">
      <main className="app-main">
        <Outlet />
      </main>
      <TabBar />
    </div>
  )
}
