package com.jarvisai.assistant.browser

/**
 * JavaScript payloads injected into WebViews during autonomous AI browser sessions.
 * Locks viewport scaling for deterministic desktop-class coordinate geometry and
 * suppresses Android soft keyboard popups (`inputmode="none"`) while an AI agent is typing.
 */
object BrowserAutomationScripts {

    /**
     * Generates a self-contained JavaScript snippet that toggles the automation input & viewport lock.
     *
     * When [enabled] is `true`:
     * - Locks `<meta name="viewport">` to a deterministic `width=1280, user-scalable=no` layout
     *   (saving the original viewport content in `data-ag-orig-viewport`).
     * - Attaches a `focusin` capture listener and a `MutationObserver` that forces `inputmode="none"`
     *   on `<input>`, `<textarea>`, and `[contenteditable]` nodes so the Android IME does not pop up
     *   and resize the viewport while the agent is dispatching clicks or keystrokes.
     *
     * When [enabled] is `false`:
     * - Disconnects the `MutationObserver` and `focusin` listener.
     * - Restores every element's original `inputmode` attribute and original `<meta name="viewport">`.
     */
    fun inputLock(enabled: Boolean): String {
        return """
            (function() {
                try {
                    var STATE_KEY = '__antigravityInputLockState';
                    var ATTR_ORIG_INPUTMODE = 'data-ag-orig-inputmode';
                    var ATTR_HAD_INPUTMODE = 'data-ag-had-inputmode';
                    var ATTR_ORIG_VIEWPORT = 'data-ag-orig-viewport';
                    var ATTR_CREATED_VIEWPORT = 'data-ag-created-viewport';

                    function lockElementInputMode(el) {
                        if (!el || !el.tagName) return;
                        var tag = el.tagName.toLowerCase();
                        var isEditable = (tag === 'input' || tag === 'textarea' || el.isContentEditable);
                        if (!isEditable) return;
                        if (!el.hasAttribute(ATTR_HAD_INPUTMODE)) {
                            if (el.hasAttribute('inputmode')) {
                                el.setAttribute(ATTR_HAD_INPUTMODE, 'true');
                                el.setAttribute(ATTR_ORIG_INPUTMODE, el.getAttribute('inputmode') || '');
                            } else {
                                el.setAttribute(ATTR_HAD_INPUTMODE, 'false');
                            }
                        }
                        if (el.getAttribute('inputmode') !== 'none') {
                            el.setAttribute('inputmode', 'none');
                        }
                    }

                    function unlockAllElements() {
                        var lockedNodes = document.querySelectorAll('[' + ATTR_HAD_INPUTMODE + ']');
                        for (var i = 0; i < lockedNodes.length; i++) {
                            var node = lockedNodes[i];
                            var had = node.getAttribute(ATTR_HAD_INPUTMODE) === 'true';
                            if (had) {
                                node.setAttribute('inputmode', node.getAttribute(ATTR_ORIG_INPUTMODE) || '');
                            } else {
                                node.removeAttribute('inputmode');
                            }
                            node.removeAttribute(ATTR_HAD_INPUTMODE);
                            node.removeAttribute(ATTR_ORIG_INPUTMODE);
                        }
                    }

                    function lockViewportMeta() {
                        var head = document.head || document.getElementsByTagName('head')[0];
                        if (!head) return;
                        var meta = document.querySelector('meta[name="viewport"]');
                        var targetWidth = Math.max(window.innerWidth || 0, 1280);
                        var lockedContent = 'width=' + targetWidth + ', initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                        if (meta) {
                            if (!meta.hasAttribute(ATTR_ORIG_VIEWPORT)) {
                                meta.setAttribute(ATTR_ORIG_VIEWPORT, meta.getAttribute('content') || '');
                            }
                            meta.setAttribute('content', lockedContent);
                        } else {
                            meta = document.createElement('meta');
                            meta.setAttribute('name', 'viewport');
                            meta.setAttribute(ATTR_CREATED_VIEWPORT, 'true');
                            meta.setAttribute('content', lockedContent);
                            head.appendChild(meta);
                        }
                    }

                    function unlockViewportMeta() {
                        var meta = document.querySelector('meta[name="viewport"]');
                        if (!meta) return;
                        if (meta.getAttribute(ATTR_CREATED_VIEWPORT) === 'true') {
                            if (meta.parentNode) meta.parentNode.removeChild(meta);
                        } else if (meta.hasAttribute(ATTR_ORIG_VIEWPORT)) {
                            meta.setAttribute('content', meta.getAttribute(ATTR_ORIG_VIEWPORT) || '');
                            meta.removeAttribute(ATTR_ORIG_VIEWPORT);
                        }
                    }

                    if ($enabled) {
                        if (window[STATE_KEY] && window[STATE_KEY].active) {
                            return 'already_locked';
                        }
                        lockViewportMeta();
                        var existingInputs = document.querySelectorAll('input, textarea, [contenteditable]');
                        for (var i = 0; i < existingInputs.length; i++) {
                            lockElementInputMode(existingInputs[i]);
                        }
                        var onFocusIn = function(ev) {
                            if (ev && ev.target) {
                                lockElementInputMode(ev.target);
                            }
                        };
                        document.addEventListener('focusin', onFocusIn, true);
                        var observer = new MutationObserver(function(mutations) {
                            for (var m = 0; m < mutations.length; m++) {
                                var added = mutations[m].addedNodes;
                                if (!added) continue;
                                for (var n = 0; n < added.length; n++) {
                                    var item = added[n];
                                    if (item && item.nodeType === 1) {
                                        lockElementInputMode(item);
                                        if (item.querySelectorAll) {
                                            var nested = item.querySelectorAll('input, textarea, [contenteditable]');
                                            for (var k = 0; k < nested.length; k++) {
                                                lockElementInputMode(nested[k]);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        if (document.documentElement) {
                            observer.observe(document.documentElement, { childList: true, subtree: true });
                        }
                        window[STATE_KEY] = {
                            active: true,
                            onFocusIn: onFocusIn,
                            observer: observer
                        };
                        return 'locked';
                    } else {
                        var st = window[STATE_KEY];
                        if (st) {
                            if (st.onFocusIn) {
                                document.removeEventListener('focusin', st.onFocusIn, true);
                            }
                            if (st.observer) {
                                st.observer.disconnect();
                            }
                            window[STATE_KEY] = null;
                        }
                        unlockAllElements();
                        unlockViewportMeta();
                        return 'unlocked';
                    }
                } catch (err) {
                    return 'error:' + (err && err.message ? err.message : String(err));
                }
            })();
        """.trimIndent()
    }

    /**
     * Browser MCP input & zoom lock matching AntiGem's design:
     * - Always suppresses Android soft keyboard popups (`inputmode="none"`) on editable fields.
     * - Leaves `<meta name="viewport">` completely untouched during light busy locks ([pinZoom] = false).
     * - When explicitly locked ([pinZoom] = true), pins zoom using the page's OWN layout width
     *   (`window.innerWidth`) rather than forcing `1280px`, so mobile pages (like m.youtube.com)
     *   never reflow or flash black during AI actions.
     */
    fun agentInputLock(enabled: Boolean, pinZoom: Boolean = false): String {
        return """
            (function (on, pinZoom) {
              try {
                var FIELDS = 'input, textarea, select, [contenteditable]:not([contenteditable="false"])';
                var S = window.__agentraAgentLock;
                function restoreMeta() {
                  var meta = document.querySelector('meta[name="viewport"]');
                  if (meta && S) {
                    if (S.created) meta.remove();
                    else if (S.original == null) meta.removeAttribute('content');
                    else meta.setAttribute('content', S.original);
                  }
                }
                if (!on) {
                  if (!S) return true;
                  if (S.observer) S.observer.disconnect();
                  if (S.onFocus) document.removeEventListener('focusin', S.onFocus, true);
                  if (window.visualViewport && S.pin) window.visualViewport.removeEventListener('resize', S.pin);
                  S.fields.forEach(function (original, el) {
                    if (original == null) el.removeAttribute('inputmode');
                    else el.setAttribute('inputmode', original);
                  });
                  if (S.pinning) restoreMeta();
                  window.__agentraAgentLock = null;
                  return true;
                }
                if (!S) {
                  S = { fields: new Map(), pinning: false, width: Math.max(1, Math.round(window.innerWidth || 390)) };
                  window.__agentraAgentLock = S;
                  var protect = function (el) {
                    if (!el || el.nodeType !== 1 || !el.matches || !el.matches(FIELDS)) return;
                    if (!S.fields.has(el)) S.fields.set(el, el.getAttribute('inputmode'));
                    if (el.getAttribute('inputmode') !== 'none') el.setAttribute('inputmode', 'none');
                  };
                  S.protectAll = function () { document.querySelectorAll(FIELDS).forEach(protect); };
                  S.pin = function () {
                    if (!S.pinning) return;
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                      meta = document.createElement('meta');
                      meta.name = 'viewport';
                      (document.head || document.documentElement).appendChild(meta);
                    }
                    var vv = window.visualViewport;
                    var screenWidth = vv ? vv.width * vv.scale : window.innerWidth;
                    var scale = screenWidth / S.width;
                    scale = Math.abs(scale - 1) < 0.01 ? 1 : Math.max(0.05, Math.round(scale * 10000) / 10000);
                    var content = 'width=' + S.width + ', initial-scale=' + scale + ', minimum-scale=' + scale + ', maximum-scale=' + scale + ', user-scalable=no';
                    if (meta.getAttribute('content') !== content) meta.setAttribute('content', content);
                  };
                  S.setPin = function (want) {
                    if (want && !S.pinning) {
                      var meta0 = document.querySelector('meta[name="viewport"]');
                      S.original = meta0 && meta0.getAttribute('content');
                      S.created = !meta0;
                      S.width = Math.max(1, Math.round(window.innerWidth || 390));
                      S.pinning = true;
                    } else if (!want && S.pinning) {
                      S.pinning = false;
                      restoreMeta();
                    }
                    S.pin();
                  };
                  S.onFocus = function (e) { protect(e.composedPath ? e.composedPath()[0] : e.target); };
                  S.observer = new MutationObserver(function (records) {
                    var repin = false;
                    records.forEach(function (r) {
                      if (r.type === 'attributes') {
                        if (r.attributeName === 'content' && r.target.name === 'viewport') repin = true;
                        else if (r.attributeName === 'inputmode' && S.fields.has(r.target)) protect(r.target);
                        return;
                      }
                      r.addedNodes.forEach(function (n) {
                        if (n.nodeType !== 1) return;
                        protect(n);
                        if (n.querySelectorAll) n.querySelectorAll(FIELDS).forEach(protect);
                        if (n.localName === 'meta') repin = true;
                      });
                    });
                    if (repin) S.pin();
                  });
                  if (document.documentElement) {
                    S.observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'inputmode'] });
                  }
                  document.addEventListener('focusin', S.onFocus, true);
                  if (window.visualViewport) window.visualViewport.addEventListener('resize', S.pin);
                }
                S.protectAll();
                S.setPin(pinZoom);
                return true;
              } catch (err) {
                return false;
              }
            })($enabled, $pinZoom);
        """.trimIndent()
    }
}
