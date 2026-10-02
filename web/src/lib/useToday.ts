import { useEffect, useState } from 'react'

import { todayISO } from './dates'

/**
 * Today's date, kept true while the app stays open.
 *
 * <p>An installed PWA is not a web page you close. On a phone it sits on the home
 * screen for weeks, and the tab it runs in is rarely killed. So a date read once at
 * launch quietly becomes wrong: the app spends Wednesday morning still showing
 * Tuesday, and the first tick of the day is written against Tuesday's date.
 *
 * <p>Three things can tell us the day turned, and all three are cheap:
 * <ul>
 *   <li>a timer set to fire just after the next midnight,</li>
 *   <li>the app being brought back to the foreground,</li>
 *   <li>the window regaining focus.</li>
 * </ul>
 * The timer alone isn't enough, because phones throttle and suspend timers in
 * backgrounded apps — the one case this exists for. The foreground checks alone
 * aren't enough either, for anyone who leaves it open on a desk overnight.
 */
export function useCurrentDate(): string {
  const [date, setDate] = useState(todayISO)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>

    const check = () => {
      // Only ever moves to the real current date, so React bails out on no-ops.
      setDate(todayISO())
      schedule()
    }

    const schedule = () => {
      clearTimeout(timer)
      const now = new Date()
      const midnight = new Date(now)
      midnight.setHours(24, 0, 0, 0)
      // A second past midnight, so the clock has definitely rolled over.
      timer = setTimeout(check, midnight.getTime() - now.getTime() + 1000)
    }

    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        check()
      }
    }

    schedule()
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', check)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('focus', check)
    }
  }, [])

  return date
}
