import { motion } from 'motion/react'
import { NavLink } from 'react-router'

import { spring } from '../motion/springs'
import { PlansIcon, TodayIcon } from './Icons'

const tabs = [
  { to: '/', label: 'Today', Icon: TodayIcon, end: true },
  { to: '/plans', label: 'Plans', Icon: PlansIcon, end: false },
]

/** Translucent bottom tab bar. Icons fill when active, the way iOS does it. */
export function TabBar() {
  return (
    <nav className="tabbar material" aria-label="Main">
      {tabs.map(({ to, label, Icon, end }) => (
        <NavLink key={to} to={to} end={end} className="tab">
          {({ isActive }) => (
            <motion.span
              className="tab-inner"
              whileTap={{ scale: 0.9 }}
              transition={spring.snappy}
              data-active={isActive}
            >
              <Icon filled={isActive} />
              <span className="tab-label">{label}</span>
            </motion.span>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
