import { Component, type ErrorInfo, type ReactNode } from 'react'
import { useRouteError } from 'react-router'

/**
 * The last line of defence: a screen that can always be recovered from.
 *
 * <p>Without one of these, a single thrown error unmounts the whole app and leaves
 * a white page — or, in a production build, React Router's own fallback showing a
 * minified stack trace with no way back. Either way the person is stuck, and their
 * only move is to delete the app.
 *
 * <p>There are two of them because errors arrive by two routes: one inside the
 * router for anything a screen throws while rendering, and one outside it for
 * everything else, including the router failing to start at all.
 */

function Recovery({ error }: { error: unknown }) {
  const detail = error instanceof Error ? error.message : String(error ?? 'Unknown error')

  return (
    <div className="empty-state" role="alert">
      <h2 className="t-title-3">Something broke</h2>
      <p className="t-subhead secondary">
        Not your fault, and nothing you've ticked is lost — it's all on the server.
        Reloading usually clears it.
      </p>
      <div className="empty-action">
        <button className="btn btn-primary" onClick={() => window.location.reload()}>
          Reload
        </button>
      </div>
      {/* Folded away, because the person doesn't need it — but you do, when they tell you. */}
      <details className="error-detail">
        <summary className="t-footnote secondary">What went wrong</summary>
        <p className="t-footnote secondary">{detail}</p>
      </details>
    </div>
  )
}

/** Inside the router: a screen threw while rendering. The tab bar stays usable. */
export function RouteErrorScreen() {
  const error = useRouteError()
  return <Recovery error={error} />
}

type Props = { children: ReactNode }
type State = { error: unknown }

/** Outside the router, so it still catches when the router itself is the problem. */
export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: unknown): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Nothing collects these yet; the console is what you have when someone
    // sends you a screenshot of the reload screen.
    console.error('Anything crashed:', error, info.componentStack)
  }

  render() {
    return this.state.error ? <Recovery error={this.state.error} /> : this.props.children
  }
}
