import { useState } from 'react';
import { StyleSheet } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';
import type { ShouldStartLoadRequest } from 'react-native-webview/lib/WebViewTypes';

import { diagramDocument, frameHeight, readDiagramMessage } from '@/lib/diagramShell';
import { colors } from '@/theme';

/**
 * An interactive diagram's page on iPhone and Android: a locked-down web view holding the sealed document from
 * `diagramShell.ts`. The web app uses `DiagramFrame.web.tsx` instead.
 *
 * The document's Content-Security-Policy cuts off the network. On top of that, this web view:
 * - loads only the document itself: any other address (a link, a redirect, a form) is refused before it starts;
 * - keeps no storage, cache or cookies (`incognito`), opens no windows, reads no files, and doesn't turn text into
 *   phone-number or address links;
 * - doesn't scroll on its own: it's sized to the page, and the lesson scrolls around it.
 *
 * Its only channel to the app is the bridge's messages: the page's height, and whether its script failed
 * (`onFailed`, so the caller can show the text version instead). Everything else it sends is ignored.
 */
export function DiagramFrame({ html, title, onFailed }: { html: string; title: string; onFailed: () => void }) {
  const [height, setHeight] = useState(320);

  function onMessage(event: WebViewMessageEvent) {
    const message = readDiagramMessage(event.nativeEvent.data);
    if (message?.kind === 'height') setHeight(frameHeight(message.value));
    if (message?.kind === 'error') onFailed();
  }

  return (
    <WebView
      source={{ html: diagramDocument(html), baseUrl: 'about:blank' }}
      originWhitelist={['about:blank']}
      onShouldStartLoadWithRequest={onlyTheDocument}
      onMessage={onMessage}
      onError={onFailed}
      javaScriptEnabled
      domStorageEnabled={false}
      incognito
      cacheEnabled={false}
      allowFileAccess={false}
      allowFileAccessFromFileURLs={false}
      allowUniversalAccessFromFileURLs={false}
      setSupportMultipleWindows={false}
      javaScriptCanOpenWindowsAutomatically={false}
      geolocationEnabled={false}
      allowsLinkPreview={false}
      dataDetectorTypes="none"
      mediaPlaybackRequiresUserAction
      scrollEnabled={false}
      textZoom={100}
      accessibilityLabel={`${title}: interactive diagram`}
      style={[styles.frame, { height }]}
    />
  );
}

/** The document is loaded from memory as about:blank (or a data: address on some Android versions); nothing else. */
function onlyTheDocument(request: ShouldStartLoadRequest) {
  return request.url === 'about:blank' || request.url.startsWith('data:');
}

const styles = StyleSheet.create({
  frame: { width: '100%', backgroundColor: colors.paper },
});
