import { motion, useScroll, useTransform } from 'motion/react'
import type { ReactNode } from 'react'

type ScreenProps = {
  title: string
  /** Small line above the large title, e.g. today's date. */
  eyebrow?: string
  /** A button shown on the right of the large title, e.g. "+". */
  trailing?: ReactNode
  children: ReactNode
}

/**
 * An iOS-style screen: a large title that scrolls away, handing over to a compact
 * title in a translucent bar. The bar's material only appears once content is
 * actually underneath it (a scroll edge effect, not a permanent divider).
 */
export function Screen({ title, eyebrow, trailing, children }: ScreenProps) {
  const { scrollY } = useScroll()
  // Large title is ~41px tall; fade the compact bar in as it scrolls under.
  const barOpacity = useTransform(scrollY, [16, 44], [0, 1])

  return (
    <>
      <header className="navbar" aria-hidden="true">
        <motion.div className="navbar-bg material" style={{ opacity: barOpacity }} />
        <motion.span className="navbar-title t-headline" style={{ opacity: barOpacity }}>
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
