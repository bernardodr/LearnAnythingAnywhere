import { useEffect, useRef, useState } from 'react';

import { diagramDocument, frameHeight, readDiagramMessage } from '@/lib/diagramShell';
import { colors } from '@/theme';

/**
 * An interactive diagram's page in the web app: a sandboxed iframe holding the sealed document from
 * `diagramShell.ts`. The phone version is `DiagramFrame.tsx`; Metro picks this file on the web.
 *
 * `sandbox="allow-scripts"` lets the page run its script and nothing more. Without `allow-same-origin` it gets an
 * origin of its own, so it can't read LAA's storage (the sign-in token) or reach into the app. Without
 * `allow-popups`, `allow-forms`, `allow-modals` or `allow-top-navigation` it can't open, submit, alert or navigate.
 * The page's own Content-Security-Policy cuts off the network.
 *
 * The only thing the app listens to is the bridge's messages from this frame: the page's height, and whether its
 * script failed (`onFailed`, so the caller can show the text version instead).
 *
 * **The one way out the web can't close:** a sandboxed frame may still navigate *itself* to another address (no
 * sandbox flag or CSP rule stops that, and it carries none of LAA's data, which the frame can't reach). If it does,
 * the frame loads a second time, and the app swaps the diagram for its text version at once. On phones the web view
 * refuses the navigation before it starts.
 */
export function DiagramFrame({ html, title, onFailed }: { html: string; title: string; onFailed: () => void }) {
  const frame = useRef<HTMLIFrameElement>(null);
  const loads = useRef(0);
  const [height, setHeight] = useState(320);

  useEffect(() => {
    function onMessage(event: MessageEvent) {
      // Only this diagram's own frame, and only the two messages the bridge sends.
      if (event.source !== frame.current?.contentWindow) return;
      const message = readDiagramMessage(event.data);
      if (message?.kind === 'height') setHeight(frameHeight(message.value));
      if (message?.kind === 'error') onFailed();
    }
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [onFailed]);

  return (
    <iframe
      ref={frame}
      title={`${title}: interactive diagram`}
      srcDoc={diagramDocument(html)}
      sandbox="allow-scripts"
      referrerPolicy="no-referrer"
      allow=""
      loading="lazy"
      // The document loads once; a second load means the page navigated away from it.
      onLoad={() => {
        loads.current += 1;
        if (loads.current > 1) onFailed();
      }}
      style={{ width: '100%', height, border: 0, display: 'block', background: colors.paper }}
    />
  );
}
