import { useEffect } from 'react'

/**
 * Ref'in dışına tıklandığında handler'ı çağırır.
 * NotificationDropdown ve benzeri pop-up bileşenler için kullanılır.
 */
export function useClickOutside(ref, handler) {
  useEffect(() => {
    function onMouseDown(event) {
      if (ref.current && !ref.current.contains(event.target)) {
        handler()
      }
    }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [ref, handler])
}
