import React from 'react'
import ReactDOM from 'react-dom/client'
import { ReactLenis } from 'lenis/react'
import 'lenis/dist/lenis.css'
import '@fontsource/source-serif-4/cyrillic-300.css'
import '@fontsource/source-serif-4/cyrillic-300-italic.css'
import '@fontsource/source-serif-4/latin-300.css'
import '@fontsource/source-serif-4/latin-300-italic.css'
import '@fontsource/source-serif-4/cyrillic-400.css'
import '@fontsource/source-serif-4/latin-400.css'
import App from './App'
import './styles.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ReactLenis root options={{ autoRaf: true, anchors: true, respectReducedMotion: true, stopInertiaOnNavigate: true }}>
      <App />
    </ReactLenis>
  </React.StrictMode>,
)
