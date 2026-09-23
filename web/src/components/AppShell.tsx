import { Outlet } from 'react-router'

import { OfflineBar } from './OfflineBar'
import { TabBar } from './TabBar'

/** Every screen sits inside this: content column + floating tab bar. */
export function AppShell() {
  return (
    <div className="app">
      <OfflineBar />
      <main className="app-main">
        <Outlet />
      </main>
      <TabBar />
    </div>
  )
}
