/**
 * Copies text to the clipboard.
 *
 * navigator.clipboard only exists on secure pages (https or localhost). When you
 * open the app on your phone over Wi-Fi (http://192.168.x.x) it's missing, so we
 * fall back to the old select-and-copy trick, which still works there.
 */
export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // fall through to the fallback
    }
  }

  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  area.style.fontSize = '16px' // stops iOS zooming in
  document.body.appendChild(area)
  area.select()
  area.setSelectionRange(0, text.length)
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(area)
  return ok
}
