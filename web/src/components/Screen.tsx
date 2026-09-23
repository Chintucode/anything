import { motion, useScroll, useTransform } from 'motion/react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { ChevronLeft } from './Icons'

type ScreenProps = {
  title: string
  /** Small line above the large title, e.g. today's date. */
  eyebrow?: string
  /** A button shown on the right of the large title, e.g. "+". */
  trailing?: ReactNode
  /** Back link in the top-left corner, e.g. { to: '/plans', label: 'Plans' }. */
  back?: { to: string; label: string }
  children: ReactNode
}

/**
 * An iOS-style screen: a large title that scrolls away, handing over to a compact
 * title in a translucent bar. The bar's material only appears once content is
 * actually underneath it (a scroll edge effect, not a permanent divider).
 */
export function Screen({ title, eyebrow, trailing, back, children }: ScreenProps) {
  const { scrollY } = useScroll()
  // Large title is ~41px tall; fade the compact bar in as it scrolls under.
  const barOpacity = useTransform(scrollY, [16, 44], [0, 1])

  return (
    <>
      <header className="navbar">
        <motion.div className="navbar-bg material" style={{ opacity: barOpacity }} />
        {back && (
          <Link to={back.to} className="navbar-back t-body">
            <ChevronLeft />
            {back.label}
          </Link>
        )}
        <motion.span className="navbar-title t-headline" style={{ opacity: barOpacity }} aria-hidden="true">
          {title}
        </motion.span>
      </header>

      <div className="screen">
        <div className="large-title-row">
          <div>
            {eyebrow && <p className="eyebrow t-footnote secondary">{eyebrow}</p>}
            <h1 className="t-large-title">{title}</h1>
          </div>
          {trailing}
        </div>
        {children}
      </div>
    </>
  )
}
